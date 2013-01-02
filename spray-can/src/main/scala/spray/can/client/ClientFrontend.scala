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

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import akka.event.{Logging, LoggingAdapter}
import akka.actor.ActorRef
import spray.can.{HttpEvent, HttpCommand}
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.util.ConnectionCloseReasons._
import spray.http._
import spray.io._
import HttpClientConnection._


object ClientFrontend {

  def apply(initialRequestTimeout: Long, log: LoggingAdapter): PipelineStage = {
    val warning = TaggableLog(log, Logging.WarningLevel)
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = {
        new Pipelines {
          import context.connection
          val host = connection.remoteAddress.getHostName
          val port = connection.remoteAddress.getPort
          val openRequests = mutable.Queue.empty[RequestRecord]
          var requestTimeout = initialRequestTimeout

          val commandPipeline: CPL = {
            case HttpCommand(HttpMessagePartWrapper(x: HttpRequest, ack)) =>
              if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
                render(x, ack)
                openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = System.currentTimeMillis))
              } else warning.log(connection.tag, "Received new HttpRequest before previous chunking request was " +
                "finished, ignoring...")

            case HttpCommand(HttpMessagePartWrapper(x: ChunkedRequestStart, ack)) =>
              if (openRequests.isEmpty || openRequests.last.timestamp > 0) {
                render(x, ack)
                openRequests.enqueue(new RequestRecord(x, context.sender, timestamp = 0))
              } else warning.log(connection.tag, "Received new ChunkedRequestStart before previous chunking " +
                "request was finished, ignoring...")

            case HttpCommand(HttpMessagePartWrapper(x: MessageChunk, ack)) =>
              if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
                render(x, ack)
              } else warning.log(connection.tag, "Received MessageChunk outside of chunking request context, " +
                "ignoring...")

            case HttpCommand(HttpMessagePartWrapper(x: ChunkedMessageEnd, ack)) =>
              if (!openRequests.isEmpty && openRequests.last.timestamp == 0) {
                render(x, ack)
                openRequests.last.timestamp = System.currentTimeMillis // only start timer once the request is completed
              } else warning.log(connection.tag, "Received ChunkedMessageEnd outside of chunking request " +
                "context, ignoring...")

            case SetRequestTimeout(timeout) => requestTimeout = timeout.toMillis

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case HttpEvent(x: HttpMessageEnd) =>
              if (!openRequests.isEmpty) {
                dispatch(openRequests.dequeue().sender, x)
              } else {
                warning.log(connection.tag, "Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Close(ProtocolError("Received unmatched response part " + x)))
              }

            case HttpEvent(x: HttpMessagePart) =>
              if (!openRequests.isEmpty) {
                dispatch(openRequests.head.sender, x)
              } else {
                warning.log(connection.tag, "Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Close(ProtocolError("Received unmatched response part " + x)))
              }

            case IOClientConnection.AckEvent(ack) =>
              if (!openRequests.isEmpty) dispatch(openRequests.head.sender, ack)
              else throw new IllegalStateException

            case x: Closed =>
              openRequests.foreach(rec => dispatch(rec.sender, x))
              eventPL(x) // terminates the connection actor and informs the original commander

            case TickGenerator.Tick =>
              checkForTimeout()
              eventPL(TickGenerator.Tick)

            case x: CommandException =>
              warning.log(connection.tag, "Received {}, closing connection ...", x)
              commandPL(Close(ProtocolError(x.toString)))

            case ev => eventPL(ev)
          }

          def render(part: HttpRequestPart, ack: Option[Any]) {
            commandPL(HttpRequestPartRenderingContext(part, host, port, ack))
          }

          def dispatch(receiver: ActorRef, msg: Any) {
            commandPL(IOClientConnection.Tell(receiver, msg, context.self))
          }

          def checkForTimeout() {
            if (!openRequests.isEmpty && requestTimeout > 0) {
              val rec = openRequests.head
              if (rec.timestamp > 0 && rec.timestamp + requestTimeout < System.currentTimeMillis) {
                commandPL(Close(RequestTimeout))
              }
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

  case class SetRequestTimeout(timeout: FiniteDuration) extends Command {
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }
}