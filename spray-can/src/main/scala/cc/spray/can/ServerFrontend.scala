/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray
package can

import config.HttpServerConfig
import model._
import io._
import pipelines.MessageHandlerDispatch
import rendering.HttpResponsePartRenderingContext
import akka.event.LoggingAdapter
import akka.actor.ActorRef
import akka.spray.MinimalActorRef
import collection.mutable.Queue
import annotation.tailrec

object ServerFrontend {

  def apply(config: HttpServerConfig, log: LoggingAdapter) = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
      val openRequests = Queue.empty[RequestRecord]
      var requestTimeout = config.requestTimeout
      var lastRequestSender: ActorRef = _

      @tailrec
      def commandPipeline(command: Command) {
        command match {
          case part: HttpResponsePart with HttpMessageEndPart =>
            ensureRequestOpenFor(part)
            val rec = openRequests.head
            dispatchPart(part, rec.request)
            if (rec.hasQueuedResponses) {
              commandPipeline(rec.dequeue)
            } else {
              openRequests.dequeue()
              if (openRequests.head.hasQueuedResponses)
                commandPipeline(openRequests.head.dequeue)
            }

          case part: HttpResponsePart =>
            ensureRequestOpenFor(part)
            val rec = openRequests.head
            rec.timestamp = 0L // disable request timeout checking once the first response part has come in
            dispatchPart(part, rec.request)

          case response: Response if response.rec == openRequests.head =>
            commandPipeline(response.msg) // in order response, dispatch

          case response: Response =>
            response.rec.enqueue(response.msg) // out of order response, queue up

          case cmd => commandPL(cmd)
        }
      }

      def eventPipeline(event: Event) {
        import MessageHandlerDispatch._
        def dispatchNewMessage(msg: Any, rec: RequestRecord) {
          openRequests += rec
          lastRequestSender = requestSender(rec)
          commandPL(DispatchNewMessage(msg, lastRequestSender))
        }
        def dispatchFollowupMessage(msg: Any) {
          if (lastRequestSender == null) // part before start shouldn't be allowed by the request parsing stage
            throw new IllegalStateException
          commandPL(DispatchFollowupMessage(msg, lastRequestSender))
        }
        event match {
          case x: HttpRequest => dispatchNewMessage(x, new RequestRecord(x, System.currentTimeMillis))
          case x: ChunkedRequestStart => dispatchNewMessage(x, new RequestRecord(x.request, 0L))
          case x: MessageChunk => dispatchFollowupMessage(x)
          case x: ChunkedMessageEnd => // only start request timeout checking after request has been completed
            if (openRequests.isEmpty) throw new IllegalStateException
            openRequests.last.timestamp = System.currentTimeMillis
            dispatchFollowupMessage(x)
          case x: HttpServer.SendCompleted => dispatchFollowupMessage(x)
          case x: HttpServer.Closed => dispatchFollowupMessage(x)
          case ev => eventPL(ev)
        }
      }

      def dispatchPart(part: HttpResponsePart, request: HttpRequest) {
        commandPL {
          HttpResponsePartRenderingContext(part, request.method, request.protocol, request.connectionHeader)
        }
      }

      def ensureRequestOpenFor(part: HttpResponsePart) {
        if (openRequests.isEmpty)
          throw new IllegalStateException("Received ResponsePart '" + part + "' for non-existing request")
      }

      def requestSender(rec: RequestRecord): ActorRef = new MinimalActorRef(context.self) {
        override def !(message: Any)(implicit sender: ActorRef) {
          message match {
            case x: Command =>
              context.self ! new Response(rec, x)
            case _ =>
              throw new IllegalArgumentException("Illegal response " + message + " to HTTP request " + rec.request)
          }
        }
      }
    }
  }

  private class RequestRecord(val request: HttpRequest, var timestamp: Long) {
    private var responses: Queue[Command] = _
    def enqueue(msg: Command) {
      if (responses == null) responses = Queue(msg)
      else responses.enqueue(msg)
    }
    def hasQueuedResponses = responses != null && !responses.isEmpty
    def dequeue = responses.dequeue()
  }

  private class Response(val rec: RequestRecord, val msg: Command) extends Command

}