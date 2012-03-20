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

import model._
import io._
import pipelines.{TickGenerator, MessageHandlerDispatch}
import rendering.HttpResponsePartRenderingContext
import akka.event.LoggingAdapter
import akka.actor.ActorRef
import akka.spray.MinimalActorRef
import collection.mutable.Queue
import annotation.tailrec
import MessageHandlerDispatch._

object ServerFrontend {
  import HttpServer._

  def apply(settings: ServerSettings,
            messageHandler: MessageHandler,
            timeoutResponse: HttpRequest => HttpResponse,
            log: LoggingAdapter): DoublePipelineStage = {
    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): Pipelines = {
        new Pipelines with MessageHandlerDispatch {
          val openRequests = Queue.empty[RequestRecord]
          val unconfirmedSends = Queue.empty[RequestRecord]
          val handlerCreator = messageHandlerCreator(messageHandler, context)
          var requestTimeout = settings.RequestTimeout
          var timeoutTimeout = settings.TimeoutTimeout

          @tailrec
          def commandPipeline(command: Command) {
            command match {
              case part: HttpResponsePart with HttpMessageEndPart =>
                ensureRequestOpenFor(part)
                val rec = openRequests.head
                sendPart(part, rec)
                if (rec.hasQueuedResponses) {
                  commandPipeline(rec.dequeue)
                } else {
                  openRequests.dequeue()
                  if (!openRequests.isEmpty && openRequests.head.hasQueuedResponses)
                    commandPipeline(openRequests.head.dequeue)
                }

              case part: HttpResponsePart =>
                ensureRequestOpenFor(part)
                val rec = openRequests.head
                rec.timestamp = 0L // disable request timeout checking once the first response part has come in
                sendPart(part, rec)

              case response: Response if response.rec == openRequests.head =>
                commandPipeline(response.msg) // in order response, dispatch

              case response: Response =>
                response.rec.enqueue(response.msg) // out of order response, queue up

              case x: SetRequestTimeout => requestTimeout = x.timeout.toMillis
              case x: SetTimeoutTimeout => timeoutTimeout = x.timeout.toMillis

              case cmd => commandPL(cmd)
            }
          }

          def eventPipeline(event: Event) {
            event match {
              case x: HttpRequest => dispatchNewMessage(x, x, System.currentTimeMillis)

              case x: ChunkedRequestStart => dispatchNewMessage(x, x.request, 0L)

              case x: MessageChunk => dispatchFollowupMessage(x)

              case x: ChunkedMessageEnd =>
                if (openRequests.isEmpty) throw new IllegalStateException
                // only start request timeout checking after request has been completed
                openRequests.last.timestamp = System.currentTimeMillis
                dispatchFollowupMessage(x)

              case x: SendCompleted =>
                if (unconfirmedSends.isEmpty) throw new IllegalStateException
                val rec = unconfirmedSends.dequeue()
                commandPL(IoServer.Tell(rec.handler, x, rec.receiver))

              case x: Closed =>
                val dispatch: RequestRecord => Unit = r => commandPL(IoServer.Tell(r.handler, x, r.receiver))
                unconfirmedSends.foreach(dispatch)
                openRequests.foreach(dispatch)

              case TickGenerator.Tick =>
                checkForTimeouts()
                eventPL(event)

              case ev => eventPL(ev)
            }
          }

          def sendPart(part: HttpResponsePart, rec: RequestRecord) {
            commandPL {
              import rec.request._
              HttpResponsePartRenderingContext(part, method, protocol, connectionHeader)
            }
            if (settings.ConfirmedSends) unconfirmedSends.enqueue(rec)
          }

          def ensureRequestOpenFor(part: HttpResponsePart) {
            if (openRequests.isEmpty)
              throw new IllegalStateException("Received ResponsePart '" + part + "' for non-existing request")
          }

          def dispatchNewMessage(msg: Any, request: HttpRequest, timestamp: Long) {
            val rec = new RequestRecord(request, handlerCreator(), timestamp)
            rec.receiver = if (settings.DirectResponding) context.self else newReceiver(rec)
            openRequests += rec
            commandPL(IoServer.Tell(rec.handler, msg, rec.receiver))
          }

          def dispatchFollowupMessage(msg: Any) {
            if (openRequests.isEmpty) // part before start shouldn't be allowed by the request parsing stage
              throw new IllegalStateException
            val rec = openRequests.last
            commandPL(IoServer.Tell(rec.handler, msg, rec.receiver))
          }

          def newReceiver(rec: RequestRecord): ActorRef = new MinimalActorRef(context.self) {
            override def !(message: Any)(implicit sender: ActorRef) {
              message match {
                case x: Command =>
                  context.self ! new Response(rec, x)
                case _ =>
                  throw new IllegalArgumentException("Illegal response " + message + " to HTTP request " + rec.request)
              }
            }
          }

          @tailrec
          def checkForTimeouts() {
            if (!openRequests.isEmpty && requestTimeout > 0) {
              val rec = openRequests.head
              if (rec.timestamp > 0) {
                if (rec.timestamp + requestTimeout < System.currentTimeMillis) {
                  val timeoutHandler = if (settings.TimeoutHandler.isEmpty) rec.handler
                                       else context.connectionActorContext.actorFor(settings.TimeoutHandler)
                  commandPipeline(IoServer.Tell(timeoutHandler, RequestTimeout(rec.request), rec.receiver))
                  // we record the time of the Timeout dispatch as negative timestamp value
                  rec.timestamp = -System.currentTimeMillis
                }
              } else if (rec.timestamp < 0 && timeoutTimeout > 0) {
                if (-rec.timestamp + timeoutTimeout < System.currentTimeMillis) {
                  commandPipeline(timeoutResponse(rec.request))
                  checkForTimeouts() // check potentially pending requests for timeouts
                }
              }
            }
          }
        }
      }
    }
  }

  private class RequestRecord(val request: HttpRequest, val handler: ActorRef, var timestamp: Long) {
    var receiver: ActorRef = _
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