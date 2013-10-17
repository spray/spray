/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.io.Tcp
import spray.can.rendering.RequestPartRenderingContext
import spray.can.Http
import spray.http._
import spray.io._
import spray.util.Timestamp

private object ClientFrontend {

  def apply(initialRequestTimeout: Duration): PipelineStage = {
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          var openRequests = Queue.empty[RequestRecord]
          var requestTimeout = initialRequestTimeout
          var closeCommanders = Set.empty[ActorRef]

          def lastRequestComplete = openRequests.isEmpty || openRequests.last.state.isComplete

          val commandPipeline: CPL = {
            case Http.MessageCommand(HttpMessagePartWrapper(x: HttpRequest, ack)) if closeCommanders.isEmpty ⇒
              if (lastRequestComplete) {
                render(x, x, ack)
                openRequests = openRequests enqueue new RequestRecord(x, context.sender, state = Complete(Timestamp.now))
              } else log.warning("Received new HttpRequest before previous chunking request was " +
                "finished, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: ChunkedRequestStart, ack)) if closeCommanders.isEmpty ⇒
              if (lastRequestComplete) {
                render(x, x.request, ack)
                openRequests = openRequests enqueue new RequestRecord(x, context.sender, state = WaitingForChunkedEnd)
              } else log.warning("Received new ChunkedRequestStart before previous chunking " +
                "request was finished, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: MessageChunk, ack)) if closeCommanders.isEmpty ⇒
              if (!lastRequestComplete) {
                render(x, openRequests.last.request.message, ack)
              } else log.warning("Received MessageChunk outside of chunking request context, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: ChunkedMessageEnd, ack)) if closeCommanders.isEmpty ⇒
              if (!lastRequestComplete) {
                render(x, openRequests.last.request.message, ack)
                openRequests.last.state = Complete(Timestamp.now) // only start timer once the request is completed
              } else log.warning("Received ChunkedMessageEnd outside of chunking request " +
                "context, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: HttpRequestPart, _)) if !closeCommanders.isEmpty ⇒
              log.error("Received {} after CloseCommand, ignoring", x)

            case x: Http.CloseCommand ⇒
              closeCommanders += context.sender
              commandPL(x)

            case CommandWrapper(SetRequestTimeout(timeout)) ⇒ requestTimeout = timeout

            case cmd                                        ⇒ commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case Http.MessageEvent(x: HttpMessageEnd) ⇒
              if (!openRequests.isEmpty) {
                val currentRecord = openRequests.head
                openRequests = openRequests.tail
                dispatch(currentRecord.sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Http.Close)
              }

            case Http.MessageEvent(x: HttpMessagePart) ⇒
              if (!openRequests.isEmpty) {
                dispatch(openRequests.head.sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Http.Close)
              }

            case Pipeline.AckEvent(ack) ⇒
              if (!openRequests.isEmpty) dispatch(openRequests.head.sender, ack)
              else throw new IllegalStateException

            case x: Tcp.ConnectionClosed ⇒
              openRequests.foldLeft(closeCommanders)(_ + _.sender) foreach (dispatch(_, x))
              eventPL(x) // terminates the connection actor

            case TickGenerator.Tick ⇒
              checkForTimeout()
              eventPL(TickGenerator.Tick)

            case Tcp.CommandFailed(Tcp.Write(_, Tcp.NoAck(PartAndSender(part, requestSender)))) ⇒
              dispatch(requestSender, Http.SendFailed(part))

            case Tcp.CommandFailed(Tcp.Write(_, ack)) ⇒
              log.warning("Sending of HttpRequestPart with ack {} failed, write command dropped", ack)

            case ev ⇒ eventPL(ev)
          }

          def render(part: HttpRequestPart, message: HttpMessage, ack: Option[Any]): Unit = {
            val sentAck = ack match {
              case Some(x) ⇒ Pipeline.AckEvent(x)
              case None    ⇒ Tcp.NoAck(PartAndSender(part, context.sender))
            }
            commandPL(RequestPartRenderingContext(part, message.protocol, sentAck))
          }

          def checkForTimeout(): Unit =
            if (!openRequests.isEmpty && requestTimeout.isFinite) {
              val rec = openRequests.head
              if (rec.state.isOverdue(requestTimeout)) {
                log.warning("Request timed out after {}, closing connection", requestTimeout)
                dispatch(rec.sender, Timedout(rec.request))
                commandPL(Http.Close)
              }
            }

          def dispatch(receiver: ActorRef, msg: Any): Unit =
            commandPL(Pipeline.Tell(receiver, msg, context.self))
        }
    }
  }

  sealed trait RequestState {
    def isComplete: Boolean
    def isOverdue(timeout: Duration): Boolean
  }
  case object WaitingForChunkedEnd extends RequestState {
    def isComplete: Boolean = false
    def isOverdue(timeout: Duration): Boolean = false
  }
  case class Complete(timestamp: Timestamp) extends RequestState {
    def isComplete: Boolean = true
    def isOverdue(timeout: Duration): Boolean = (timestamp + timeout).isPast
  }
  private class RequestRecord(val request: HttpRequestPart with HttpMessageStart, val sender: ActorRef, var state: RequestState)

  private case class PartAndSender(part: HttpRequestPart, sender: ActorRef)
}
