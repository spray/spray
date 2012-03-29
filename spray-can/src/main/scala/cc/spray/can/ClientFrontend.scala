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

package cc.spray.can

import model._
import cc.spray.io._
import akka.event.LoggingAdapter
import akka.actor.ActorRef
import collection.mutable.Queue
import pipelines.TickGenerator
import rendering.HttpRequestPartRenderingContext
import akka.util.Duration

object ClientFrontend {

  def apply(initialRequestTimeout: Long, log: LoggingAdapter) = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
      val host = context.handle.address.getHostName
      val port = context.handle.address.getPort
      val openRequests = Queue.empty[RequestRecord]
      var requestTimeout = initialRequestTimeout

      def commandPipeline(command: Command) {
        command match {
          case x: HttpRequest =>
            if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
              render(x)
              openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = System.currentTimeMillis))
            } else {
              log.warning("Received new HttpRequest before previous chunking request was finished, " +
                "forwarding to deadletters ...")
              forwardToDeadLetters(x)
            }

          case x: ChunkedRequestStart =>
            if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
              render(x)
              openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = 0))
            } else {
              log.warning("Received new ChunkedRequestStart before previous chunking request was finished, " +
                "forwarding to deadletters ...")
              forwardToDeadLetters(x)
            }

          case x: MessageChunk =>
            if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
              render(x)
            } else {
              log.warning("Received MessageChunk outside of chunking request context, ignoring...")
              forwardToDeadLetters(x)
            }

          case x: ChunkedMessageEnd =>
            if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
              render(x)
              openRequests.last.timestamp = System.currentTimeMillis // only start timer once the request is completed
            } else {
              log.warning("Received ChunkedMessageEnd outside of chunking request context, ignoring...")
              forwardToDeadLetters(x)
            }

          case x: SetRequestTimeout => requestTimeout = x.timeout.toMillis

          case cmd => commandPL(cmd)
        }
      }

      def eventPipeline(event: Event) {
        event match {
          case x: HttpMessageEndPart =>
            if (!openRequests.isEmpty) {
              dispatch(openRequests.dequeue().sender, x)
            } else {
              log.warning("Received unmatched {}, closing connection due to protocol error", x)
              commandPL(HttpClient.Close(ProtocolError("Received unmatched response part " + x)))
            }

          case x: HttpMessagePart =>
            if (!openRequests.isEmpty) {
              dispatch(openRequests.head.sender, x)
            } else {
              log.warning("Received unmatched {}, closing connection due to protocol error", x)
              commandPL(HttpClient.Close(ProtocolError("Received unmatched response part " + x)))
            }

          case x: HttpClient.SendCompleted =>
            if (!openRequests.isEmpty) {
              dispatch(openRequests.head.sender, x)
            } else log.warning("Received stray SendCompleted")

          case x: HttpClient.Closed =>
            openRequests.foreach(rec => dispatch(rec.sender, x))

          case TickGenerator.Tick =>
            checkForTimeout()
            eventPL(event)

          case ev => eventPL(ev)
        }
      }

      def forwardToDeadLetters(x: AnyRef) {
        context.connectionActorContext.system.deadLetters ! x
      }

      def render(part: HttpRequestPart) {
        commandPL(HttpRequestPartRenderingContext(part, host, port))
      }

      def dispatch(receiver: ActorRef, msg: AnyRef) {
        commandPL(HttpClient.Tell(receiver, msg, context.self))
      }

      def checkForTimeout() {
        if (!openRequests.isEmpty && requestTimeout > 0) {
          val rec = openRequests.head
          if (rec.timestamp > 0 && rec.timestamp + requestTimeout < System.currentTimeMillis) {
            commandPL(HttpClient.Close(RequestTimeout))
          }
        }
      }
    }
  }

  private class RequestRecord(
    val request: HttpRequestPart with HttpMessageStartPart,
    val sender: ActorRef,
    var timestamp: Long
  )

  ////////////// COMMANDS //////////////

  case class SetRequestTimeout(timeout: Duration) extends Command {
    require(!timeout.isFinite, "timeout must not be infinite, set to zero to disable")
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }
}