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

          def lastRequestComplete = openRequests.isEmpty || openRequests.last.state != AwaitingChunkEnd

          val commandPipeline: CPL = {
            case Http.MessageCommand(HttpMessagePartWrapper(x: HttpRequest, ack)) if closeCommanders.isEmpty ⇒
              if (lastRequestComplete) {
                render(x, x, ack)
                val state = if (openRequests.isEmpty) AwaitingResponseStart(Timestamp.now) else AwaitingPreviousResponseEnd
                openRequests = openRequests enqueue new RequestRecord(x, context.sender, state)
              } else log.warning("Received new HttpRequest before previous chunking request was " +
                "finished, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: ChunkedRequestStart, ack)) if closeCommanders.isEmpty ⇒
              if (lastRequestComplete) {
                render(x, x.request, ack)
                openRequests = openRequests enqueue new RequestRecord(x, context.sender, state = AwaitingChunkEnd)
              } else log.warning("Received new ChunkedRequestStart before previous chunking " +
                "request was finished, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: MessageChunk, ack)) if closeCommanders.isEmpty ⇒
              if (!lastRequestComplete) {
                render(x, openRequests.last.request.message, ack)
              } else log.warning("Received MessageChunk outside of chunking request context, ignoring...")

            case Http.MessageCommand(HttpMessagePartWrapper(x: ChunkedMessageEnd, ack)) if closeCommanders.isEmpty ⇒
              if (!lastRequestComplete) {
                render(x, openRequests.last.request.message, ack)
                openRequests.last.state = AwaitingResponseStart(Timestamp.now) // only start timer once the request is completed
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
                if (openRequests.nonEmpty)
                  openRequests.head.state = openRequests.head.state match {
                    case AwaitingChunkEnd            ⇒ AwaitingChunkEnd
                    case AwaitingPreviousResponseEnd ⇒ AwaitingResponseStart(Timestamp.now)
                    case _: AwaitingResponseStart    ⇒ throw new IllegalStateException
                  }
                dispatch(currentRecord.sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Http.Close)
              }

            case Http.MessageEvent(x: HttpMessagePart) ⇒
              if (!openRequests.isEmpty) {
                if (x.isInstanceOf[HttpMessageStart]) openRequests.head.state = AwaitingChunkEnd
                dispatch(openRequests.head.sender, x)
              } else {
                log.warning("Received unmatched {}, closing connection due to protocol error", x)
                commandPL(Http.Close)
              }

            case AckAndSender(ack, sender) ⇒ dispatch(sender, ack)

            case x: Tcp.ConnectionClosed ⇒
              openRequests.foldLeft(closeCommanders)(_ + _.sender) foreach (dispatch(_, x))
              if (x eq Tcp.PeerClosed) commandPL(Tcp.Close)
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
              case Some(x) ⇒ AckAndSender(x, context.sender)
              case None    ⇒ Tcp.NoAck(PartAndSender(part, context.sender))
            }
            commandPL(RequestPartRenderingContext(part, message.protocol, sentAck))
          }

          def checkForTimeout(): Unit =
            if (!openRequests.isEmpty && requestTimeout.isFinite) {
              val rec = openRequests.head
              if (rec.state.isOverdue(requestTimeout)) {
                val r = rec.request.message.asInstanceOf[HttpRequest]
                log.warning("{} request to '{}' timed out after {}, closing connection", r.method, r.uri, requestTimeout)
                dispatch(rec.sender, Timedout(rec.request))
                commandPL(Http.Abort)
              }
            }

          def dispatch(receiver: ActorRef, msg: Any): Unit =
            commandPL(Pipeline.Tell(receiver, msg, context.self))
        }
    }
  }

  private sealed abstract class RequestState {
    def isOverdue(timeout: Duration): Boolean = false
  }
  private case object AwaitingChunkEnd extends RequestState
  private case object AwaitingPreviousResponseEnd extends RequestState
  private case class AwaitingResponseStart(timestamp: Timestamp) extends RequestState {
    override def isOverdue(timeout: Duration) = (timestamp + timeout).isPast
  }
  private class RequestRecord(val request: HttpRequestPart with HttpMessageStart, val sender: ActorRef, var state: RequestState)

  private case class PartAndSender(part: HttpRequestPart, sender: ActorRef)
  private[client] case class AckAndSender(ack: Any, sender: ActorRef) extends Event
}
