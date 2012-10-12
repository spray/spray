/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.can.client

import akka.event.LoggingAdapter
import akka.util.Duration
import akka.actor.ActorRef
import collection.mutable.Queue
import spray.can.{HttpEvent, HttpCommand}
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.http._
import spray.io._
import spray.util._


object ClientFrontend {

  def apply(initialRequestTimeout: Long, log: LoggingAdapter): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) = {
        new Pipelines {
          val host = context.handle.remoteAddress.getHostName
          val port = context.handle.remoteAddress.getPort
          val openRequests = Queue.empty[RequestRecord]
          var requestTimeout = initialRequestTimeout

          val commandPipeline: CPL = {
            case HttpCommand(wrapper: HttpMessagePartWrapper) if wrapper.messagePart.isInstanceOf[HttpRequestPart] =>
              wrapper.messagePart.asInstanceOf[HttpRequestPart] match {
                case x: HttpRequest =>
                  if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
                    render(wrapper)
                    openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = System.currentTimeMillis))
                  } else {
                    log.warning("Received new HttpRequest before previous chunking request was finished, " +
                      "forwarding to deadletters ...")
                    forwardToDeadLetters(x)
                  }

                case x: ChunkedRequestStart =>
                  if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
                    render(wrapper)
                    openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = 0))
                  } else {
                    log.warning("Received new ChunkedRequestStart before previous chunking request was finished, " +
                      "forwarding to deadletters ...")
                    forwardToDeadLetters(x)
                  }

                case x: MessageChunk =>
                  if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
                    render(wrapper)
                  } else {
                    log.warning("Received MessageChunk outside of chunking request context, ignoring...")
                    forwardToDeadLetters(x)
                  }

                case x: ChunkedMessageEnd =>
                  if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
                    render(wrapper)
                    openRequests.last.timestamp = System.currentTimeMillis // only start timer once the request is completed
                  } else {
                    log.warning("Received ChunkedMessageEnd outside of chunking request context, ignoring...")
                    forwardToDeadLetters(x)
                  }
              }

            case x: SetRequestTimeout => requestTimeout = x.timeout.toMillis

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case HttpEvent(x: HttpMessageEnd) =>
              if (!openRequests.isEmpty) {
                dispatch(openRequests.dequeue().sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(HttpClient.Close(ProtocolError("Received unmatched response part " + x)))
              }

            case HttpEvent(x: HttpMessagePart) =>
              if (!openRequests.isEmpty) {
                dispatch(openRequests.head.sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(HttpClient.Close(ProtocolError("Received unmatched response part " + x)))
              }

            case IOClient.AckEvent(ack) =>
              if (!openRequests.isEmpty) {
                dispatch(openRequests.head.sender, ack)
              } else throw new IllegalStateException

            case x: HttpClient.Closed =>
              openRequests.foreach(rec => dispatch(rec.sender, x))
              eventPL(x) // terminates the connection actor and informs the original commander

            case TickGenerator.Tick =>
              checkForTimeout()
              eventPL(TickGenerator.Tick)

            case x: CommandException =>
              log.warning("Received {}, closing connection ...", x)
              commandPL(HttpClient.Close(IOError(x)))

            case ev => eventPL(ev)
          }

          def forwardToDeadLetters(x: AnyRef) {
            context.connectionActorContext.system.deadLetters ! x
          }

          def render(part: HttpMessagePartWrapper) {
            commandPL(HttpRequestPartRenderingContext(part.asInstanceOf[HttpRequestPart], host, port, part.sentAck))
          }

          def dispatch(receiver: ActorRef, msg: Any) {
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
    }

  private class RequestRecord(
    val request: HttpRequestPart with HttpMessageStart,
    val sender: ActorRef,
    var timestamp: Long
  )

  ////////////// COMMANDS //////////////

  case class SetRequestTimeout(timeout: Duration) extends Command {
    require(timeout.isFinite, "timeout must not be infinite, set to zero to disable")
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }
}