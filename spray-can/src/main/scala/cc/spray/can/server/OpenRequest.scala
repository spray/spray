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

package spray.can.server

import collection.mutable
import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.io._
import spray.http._


sealed trait OpenRequest {
  def connectionActorContext: ActorContext
  def log: LoggingAdapter
  def isEmpty: Boolean
  def request: HttpRequest
  def appendToEndOfChain(openRequest: OpenRequest): OpenRequest
  def dispatchInitialRequestPartToHandler()
  def dispatchNextQueuedResponse()
  def checkForTimeout(now: Long)
  def nextIfNoAcksPending: OpenRequest

  // commands
  def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper): OpenRequest
  def handleResponsePart(part: HttpMessagePartWrapper)
  def enqueueCommand(command: Command)

  // events
  def handleMessageChunk(chunk: MessageChunk)
  def handleChunkedMessageEnd(part: ChunkedMessageEnd)
  def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver): OpenRequest
  def handleClosed(ev: HttpServer.Closed)
}

trait OpenRequestComponent { component =>
  def handlerCreator: () => ActorRef
  def connectionActorContext: ActorContext
  def log: LoggingAdapter
  def settings: ServerSettings
  def downstreamCommandPL: Pipeline[Command]
  def createTimeoutResponse: HttpRequest => HttpResponse
  def handlerReceivesClosedEvents: Boolean
  def requestTimeout: Long
  def timeoutTimeout: Long

  class DefaultOpenRequest(val request: HttpRequest,
                           private[this] val connectionHeader: Option[String],
                           private[this] var timestamp: Long) extends OpenRequest {
    private[this] val receiverRef = new ResponseReceiverRef(this)
    private[this] var handler = handlerCreator()
    private[this] var nextInChain: OpenRequest = EmptyOpenRequest
    private[this] var responseQueue: mutable.Queue[Command] = _
    private[this] var pendingSentAcks: Int = 1000 // we use an offset of 1000 for as long as the response is not finished

    def connectionActorContext = component.connectionActorContext
    def log = component.log
    def isEmpty = false

    def appendToEndOfChain(openRequest: OpenRequest): OpenRequest = {
      nextInChain = nextInChain.appendToEndOfChain(openRequest)
      this
    }

    def dispatchInitialRequestPartToHandler() {
      val requestToDispatch =
        if (request.method == HttpMethods.HEAD && settings.TransparentHeadRequests)
          request.copy(method = HttpMethods.GET)
        else request
      val partToDispatch: HttpRequestPart =
        if (timestamp == 0L) ChunkedRequestStart(requestToDispatch)
        else requestToDispatch
      downstreamCommandPL(IOServer.Tell(handler, partToDispatch, receiverRef))
    }

    def dispatchNextQueuedResponse() {
      if (responsesQueued) {
        connectionActorContext.self.tell(responseQueue.dequeue(), handler)
      }
    }

    def checkForTimeout(now: Long) {
      if (timestamp > 0) {
        if (timestamp + requestTimeout < now) {
          val timeoutHandler =
            if (settings.TimeoutHandler.isEmpty) handler
            else connectionActorContext.actorFor(settings.TimeoutHandler)
          downstreamCommandPL(IOServer.Tell(timeoutHandler, Timeout(request), receiverRef))
          timestamp = -now // we record the time of the Timeout dispatch as negative timestamp value
        }
      } else if (timestamp < -1 && timeoutTimeout > 0 && (-timestamp + timeoutTimeout < now)) {
        val response = createTimeoutResponse(request)
        // we always close the connection after a timeout-timeout
        sendPart(response.withHeaders(HttpHeaders.Connection("close") :: response.headers))
      }
      nextInChain.checkForTimeout(now) // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
    }

    def nextIfNoAcksPending = if (pendingSentAcks == 0) nextInChain else this

    /***** COMMANDS *****/

    def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper) = {
      handler = connectionActorContext.sender // remember who to send Closed events to
      sendPart(part)
      pendingSentAcks -= 1000 // remove initial offset to signal that the last part has gone out
      nextInChain.dispatchNextQueuedResponse()
      nextInChain
    }

    def handleResponsePart(part: HttpMessagePartWrapper) {
      timestamp = 0L // disable request timeout checking once the first response part has come in
      handler = connectionActorContext.sender // remember who to send Closed events to
      sendPart(part)
      dispatchNextQueuedResponse()
      this
    }

    def enqueueCommand(command: Command) {
      if (responseQueue == null) responseQueue = mutable.Queue(command)
      else responseQueue.enqueue(command)
    }

    /***** EVENTS *****/

    def handleMessageChunk(chunk: MessageChunk) {
      if (nextInChain.isEmpty)
        downstreamCommandPL(IOServer.Tell(handler, chunk, receiverRef))
      else
        // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
        nextInChain.handleMessageChunk(chunk)
    }

    def handleChunkedMessageEnd(part: ChunkedMessageEnd) {
      if (nextInChain.isEmpty) {
        // only start request timeout checking after request has been completed
        timestamp = System.currentTimeMillis
        downstreamCommandPL(IOServer.Tell(handler, part, receiverRef))
      } else
        // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
        nextInChain.handleChunkedMessageEnd(part)
    }

    def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver) = {
      downstreamCommandPL(IOServer.Tell(ev.receiver, ev.ack, receiverRef))
      pendingSentAcks -= 1
      // drop this openRequest from the unconfirmed list if we have seen the SentAck for the final response part
      if (pendingSentAcks == 0) nextInChain else this
    }

    def handleClosed(ev: HttpServer.Closed) {
      downstreamCommandPL(IOServer.Tell(handler, ev, receiverRef))
    }

    /***** PRIVATE *****/

    private def sendPart(part: HttpMessagePartWrapper) {
      val sentAck = if (part.sentAck.isEmpty) None else Some(AckEventWithReceiver(part.sentAck.get, handler))
      val cmd = HttpResponsePartRenderingContext(part.messagePart.asInstanceOf[HttpResponsePart], request.method,
                                                 request.protocol, connectionHeader, sentAck)
      downstreamCommandPL(cmd)
      if (part.sentAck.isDefined) pendingSentAcks += 1
    }

    private def responsesQueued = responseQueue != null && !responseQueue.isEmpty
  }

  object EmptyOpenRequest extends OpenRequest {
    def appendToEndOfChain(openRequest: OpenRequest) = openRequest

    def connectionActorContext = component.connectionActorContext
    def log = component.log
    def isEmpty = true
    def request = throw new IllegalStateException
    def dispatchInitialRequestPartToHandler() { throw new IllegalStateException }
    def dispatchNextQueuedResponse() {}
    def checkForTimeout(now: Long) {}
    def nextIfNoAcksPending = throw new IllegalStateException

    // commands
    def handleResponseEndAndReturnNextOpenRequest(part: HttpMessagePartWrapper) =
      handleResponsePart(part)

    def handleResponsePart(part: HttpMessagePartWrapper): Nothing =
      throw new IllegalStateException("Received ResponsePart '" + part + "' for non-existing request")

    def enqueueCommand(command: Command) {}

    // events
    def handleMessageChunk(chunk: MessageChunk) { throw new IllegalStateException }
    def handleChunkedMessageEnd(part: ChunkedMessageEnd) { throw new IllegalStateException }

    def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver) =
      throw new IllegalStateException("Received unmatched send confirmation: " + ev.ack)

    def handleClosed(ev: HttpServer.Closed) {
      if (handlerReceivesClosedEvents)
        downstreamCommandPL(IOServer.Tell(handlerCreator(), ev, connectionActorContext.self))
    }
  }

}

private[server] case class AckEventWithReceiver(ack: Any, receiver: ActorRef) extends Event