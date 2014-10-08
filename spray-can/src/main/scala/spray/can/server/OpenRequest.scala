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

package spray.can.server

import scala.collection.immutable.Queue
import scala.concurrent.duration.Duration
import akka.actor.ActorRef
import akka.spray.RefUtils
import spray.can.rendering.ResponsePartRenderingContext
import spray.io._
import spray.http._
import spray.can.Http
import spray.can.server.ServerFrontend.Context
import akka.io.Tcp
import spray.util.Timestamp
import akka.io.Tcp.NoAck

private sealed trait OpenRequest {
  def context: ServerFrontend.Context
  def isEmpty: Boolean
  def request: HttpRequest
  def appendToEndOfChain(openRequest: OpenRequest): OpenRequest
  def dispatchInitialRequestPartToHandler(handler: ActorRef)
  def dispatchNextQueuedResponse()
  def checkForTimeout(now: Timestamp)
  def nextIfNoAcksPending: OpenRequest

  // commands
  def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper): OpenRequest
  def handleResponsePart(part: HttpMessagePartWrapper)
  def enqueueCommand(command: Command, sender: ActorRef)
  def registerChunkHandler(handler: ActorRef)

  // events
  def handleMessageChunk(chunk: MessageChunk)
  def handleChunkedMessageEnd(part: ChunkedMessageEnd)
  def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver): OpenRequest
  def closedEventHandlers: Set[ActorRef]

  def isWaitingForChunkHandler: Boolean
}

private trait OpenRequestComponent { component ⇒
  def context: ServerFrontend.Context
  def settings: ServerSettings
  def downstreamCommandPL: Pipeline[Command]
  def requestTimeout: Duration
  def timeoutTimeout: Duration

  class DefaultOpenRequest(val request: HttpRequest,
                           private[this] var closeAfterResponseCompletion: Boolean,
                           private[this] var state: RequestState) extends OpenRequest {
    private[this] val receiverRef = new ResponseReceiverRef(this)
    private[this] var nextInChain: OpenRequest = EmptyOpenRequest
    private[this] var responseQueue = Queue.empty[(Command, ActorRef)]
    private[this] var pendingSentAcks: Int = 1000 // we use an offset of 1000 for as long as the response is not finished

    def context: Context = component.context
    def isEmpty = false

    def appendToEndOfChain(openRequest: OpenRequest): OpenRequest = {
      nextInChain = nextInChain appendToEndOfChain openRequest
      this
    }

    def dispatchInitialRequestPartToHandler(handler: ActorRef): Unit = {
      val requestToDispatch =
        if (request.method == HttpMethods.HEAD && settings.transparentHeadRequests)
          request.copy(method = HttpMethods.GET)
        else request
      val partToDispatch: HttpRequestPart =
        state match {
          case _: WaitingForChunkHandler ⇒ ChunkedRequestStart(requestToDispatch)
          case _                         ⇒ requestToDispatch
        }
      if (context.log.isDebugEnabled)
        context.log.debug("Dispatching {} to handler {}", format(partToDispatch), handler)
      downstreamCommandPL(Pipeline.Tell(context.handler, partToDispatch, receiverRef))
    }

    def dispatchNextQueuedResponse(): Unit =
      if (responsesQueued) {
        val (cmd, sender) = responseQueue.head
        context.self.tell(cmd, sender)
        responseQueue = responseQueue.tail
      }

    def checkForTimeout(now: Timestamp): Unit = {
      state match {
        case w: WaitingForChunkHandler ⇒
          if ((w.timestamp + settings.chunkHandlerRegistrationTimeout).isPast(now)) {
            context.log.warning("A chunk handler wasn't registered timely. Aborting the connection.")
            downstreamCommandPL(Tcp.Abort)
          }
        case _: ReceivingRequestChunks  ⇒
        case _: StreamingResponseChunks ⇒
        case WaitingForResponse(handler, timestamp) ⇒
          if ((timestamp + requestTimeout).isPast(now)) {
            val timeoutHandler =
              if (settings.timeoutHandler.isEmpty) handler
              else context.actorContext.actorFor(settings.timeoutHandler)
            if (RefUtils.isLocal(timeoutHandler))
              downstreamCommandPL(Pipeline.Tell(timeoutHandler, Timedout(request), receiverRef))
            else context.log.warning("The TimeoutHandler '{}' is not a local actor and thus cannot be used as a " +
              "timeout handler", timeoutHandler)
            state = WaitingForTimeoutResponse()
          }
        case WaitingForTimeoutResponse(timestamp) ⇒
          if ((timestamp + timeoutTimeout).isPast(now)) {
            val response = timeoutResponse(request)
            // we always close the connection after a timeout-timeout
            sendPart(response.withHeaders(HttpHeaders.Connection("close") :: response.headers))
          }
        case _: WaitingForFinalResponseAck ⇒
      }
      nextInChain checkForTimeout now // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
    }

    def nextIfNoAcksPending = if (pendingSentAcks == 0) nextInChain else this

    def timeoutResponse(request: HttpRequest): HttpResponse = HttpResponse(
      status = 500,
      entity = "Ooops! The server was not able to produce a timely response to your request.\n" +
        "Please try again in a short while!")

    /***** COMMANDS *****/

    def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper) = {
      state = WaitingForFinalResponseAck(context.sender)
      sendPart(part)
      pendingSentAcks -= 1000 // remove initial offset to signal that the last part has gone out
      nextInChain.dispatchNextQueuedResponse()
      nextInChain
    }

    def handleResponsePart(part: HttpMessagePartWrapper): Unit = {
      state = StreamingResponseChunks(context.sender) // disable request timeout checking once the first response part has come in
      sendPart(part)
      dispatchNextQueuedResponse()
    }

    def enqueueCommand(command: Command, sender: ActorRef): Unit =
      responseQueue = responseQueue enqueue ((command, sender))

    def registerChunkHandler(handler: ActorRef): Unit = {
      def dispatch(part: HttpRequestPart) = downstreamCommandPL(Pipeline.Tell(handler, part, receiverRef))

      downstreamCommandPL(Tcp.ResumeReading) // counter-part to receiving ChunkedRequestStart in ServerFrontend

      state =
        state match {
          case WaitingForChunkHandlerBuffering(_, receiveds) ⇒ { receiveds.foreach(dispatch); ReceivingRequestChunks(handler) }
          case WaitingForChunkHandlerReceivedAll(_, receiveds) ⇒ { receiveds.foreach(dispatch); WaitingForResponse(handler) }
          case x ⇒ throw new IllegalStateException("Didn't expect " + x)
        }
    }

    /***** EVENTS *****/

    def handleMessageChunk(chunk: MessageChunk): Unit =
      state match {
        case WaitingForChunkHandlerBuffering(timeout, receiveds) ⇒ state = WaitingForChunkHandlerBuffering(timeout, receiveds.enqueue(chunk))
        case ReceivingRequestChunks(chunkHandler) ⇒ downstreamCommandPL(Pipeline.Tell(chunkHandler, chunk, receiverRef))
        case x if nextInChain.isEmpty ⇒ throw new IllegalArgumentException(s"$this Didn't expect message chunks in state $state")
        case _ ⇒ nextInChain handleMessageChunk chunk
      }

    def handleChunkedMessageEnd(part: ChunkedMessageEnd): Unit =
      state match {
        case WaitingForChunkHandlerBuffering(timeout, receiveds) ⇒ state = WaitingForChunkHandlerReceivedAll(timeout, receiveds.enqueue(part))
        case ReceivingRequestChunks(chunkHandler) ⇒
          state = WaitingForResponse(chunkHandler)
          downstreamCommandPL(Pipeline.Tell(chunkHandler, part, receiverRef))
        case x if nextInChain.isEmpty ⇒ throw new IllegalArgumentException(s"$this Didn't expect ChunkedMessageEnd in state $state")
        case _                        ⇒ nextInChain handleChunkedMessageEnd part
      }

    def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver) = {
      if (!ev.ack.isInstanceOf[NoAck]) downstreamCommandPL(Pipeline.Tell(ev.receiver, ev.ack, receiverRef))
      pendingSentAcks -= 1
      // drop this openRequest from the unconfirmed list if we have seen the SentAck for the final response part
      if (pendingSentAcks == 0) nextInChain else this
    }

    def closedEventHandlers: Set[ActorRef] =
      nextInChain.closedEventHandlers + (state match {
        case ReceivingRequestChunks(chunkHandler)   ⇒ chunkHandler
        case WaitingForResponse(handler, _)         ⇒ handler
        case StreamingResponseChunks(lastSender)    ⇒ lastSender
        case WaitingForFinalResponseAck(lastSender) ⇒ lastSender
        case _                                      ⇒ context.handler
      })

    /***** PRIVATE *****/

    private def sendPart(part: HttpMessagePartWrapper): Unit = {
      val responsePart = part.messagePart.asInstanceOf[HttpResponsePart]
      val ack = AckEventWithReceiver(part.ack.getOrElse(NoAck), responsePart, context.sender)
      pendingSentAcks += 1
      val cmd = ResponsePartRenderingContext(responsePart, request.method, request.protocol,
        closeAfterResponseCompletion, ack)
      downstreamCommandPL(cmd)
    }

    private def responsesQueued = responseQueue != null && !responseQueue.isEmpty

    private def format(part: HttpMessagePart) = part match {
      case x: HttpRequestPart with HttpMessageStart ⇒
        val request = x.message.asInstanceOf[HttpRequest]
        s"${request.method} request to ${request.uri}"
      case MessageChunk(body, _) ⇒ body.length.toString + " byte request chunk"
      case x                     ⇒ x.toString
    }

    def isWaitingForChunkHandler: Boolean = state.isInstanceOf[WaitingForChunkHandler]
  }

  object EmptyOpenRequest extends OpenRequest {
    def appendToEndOfChain(openRequest: OpenRequest) = openRequest
    def context: Context = component.context
    def isEmpty = true
    def request = throw new IllegalStateException
    def dispatchInitialRequestPartToHandler(handler: ActorRef): Unit = { throw new IllegalStateException }
    def dispatchNextQueuedResponse(): Unit = {}
    def checkForTimeout(now: Timestamp): Unit = {}
    def nextIfNoAcksPending = throw new IllegalStateException

    // commands
    def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper) =
      handleResponsePart(part)

    def handleResponsePart(part: HttpMessagePartWrapper): Nothing =
      throw new IllegalStateException("Received ResponsePart '" + part + "' for non-existing request")

    def enqueueCommand(command: Command, sender: ActorRef): Unit = {}

    def registerChunkHandler(handler: ActorRef): Unit =
      throw new IllegalStateException("Received RegisterChunkHandler for non-existing request")

    // events
    def handleMessageChunk(chunk: MessageChunk): Unit = { throw new IllegalStateException }
    def handleChunkedMessageEnd(part: ChunkedMessageEnd): Unit = { throw new IllegalStateException }

    def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver) =
      throw new IllegalStateException("Received unmatched send confirmation: " + ev.ack)

    def closedEventHandlers: Set[ActorRef] = Set.empty
    def isWaitingForChunkHandler: Boolean = false
  }

}

private[server] case class AckEventWithReceiver(ack: Any, part: HttpResponsePart, receiver: ActorRef) extends Event

/**
 * The state of a request. State transformations:
 *
 * Initial state:
 *   -> WaitingForChunkHandlerBuffering: if incoming request is chunked
 *   -> WaitingForResponse: if request was received completely
 *
 * WaitingForChunkHandlerBuffering:
 *   -> WaitingForChunkHandlerReceivedAll: if ChunkedMessageEnd was received before chunk handler was registered
 *   -> ReceivingRequestChunks: after chunk handler was registered
 *
 * WaitingForChunkHandlerReceivedAll:
 *   -> WaitingForResponse: after chunk handler was registered
 *
 * ReceivingRequestChunks:
 *   -> WaitingForResponse: after ChunkedMessageEnd was received and dispatched
 *
 * WaitingForResponse:
 *   -> -finish-: if complete response was received
 *   -> StreamingResponseChunks: after a ChunkedResponseStart was sent
 *   -> WaitingForTimeoutResponse: if the timeout triggered before a response (start) was produced
 *
 * StreamingResponseStart:
 *   -> -finish-: if ChunkedMessageEnd was sent
 *
 * WaitingForTimeoutResponse:
 *   -> -finish-: if the timeout response was delivered
 *   -> -finish-: if the timeout timeout triggered before the timeout response was produced
 *
 */
private sealed trait RequestState
private sealed abstract class WaitingForChunkHandler extends RequestState {
  def timestamp: Timestamp
  def receivedChunks: Queue[HttpRequestPart]
}
/** Got a ChunkedRequestStart, waiting for chunk handler to register */
private case class WaitingForChunkHandlerBuffering(
  timestamp: Timestamp = Timestamp.now,
  receivedChunks: Queue[HttpRequestPart] = Queue.empty) extends WaitingForChunkHandler
/** Got ChunkedMessageEnd, waiting for chunk handler to register */
private case class WaitingForChunkHandlerReceivedAll(
  timestamp: Timestamp,
  receivedChunks: Queue[HttpRequestPart]) extends WaitingForChunkHandler
/** Got the chunk handler, receiving and dispatching request chunks */
private case class ReceivingRequestChunks(chunkHandler: ActorRef) extends RequestState
/** Request was fully delivered waiting for response */
private case class WaitingForResponse(handler: ActorRef, timestamp: Timestamp = Timestamp.now) extends RequestState
/** ChunkedRequestStart was sent waiting for remaining chunks */
private case class StreamingResponseChunks(lastSender: ActorRef) extends RequestState
/** Timed-out while waiting for request, `Timeout` was dispatched, now waiting for timeout response */
private case class WaitingForTimeoutResponse(timestamp: Timestamp = Timestamp.now) extends RequestState
/** Waiting for ack of last ResponseEnd */
private case class WaitingForFinalResponseAck(lastSender: ActorRef) extends RequestState
