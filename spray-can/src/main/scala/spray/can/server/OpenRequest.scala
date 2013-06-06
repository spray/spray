/*
 * Copyright (C) 2011-2013 spray.io
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
import akka.util.Duration
import akka.actor.ActorRef
import akka.spray.RefUtils
import spray.can.rendering.ResponsePartRenderingContext
import spray.io._
import spray.http._
import spray.can.Http
import spray.can.server.ServerFrontend.Context
import akka.io.Tcp

sealed trait OpenRequest {
  def context: ServerFrontend.Context
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
  def handleClosed(ev: Http.ConnectionClosed)
}

trait OpenRequestComponent { component ⇒
  def context: ServerFrontend.Context
  def settings: ServerSettings
  def downstreamCommandPL: Pipeline[Command]
  def requestTimeout: Duration
  def timeoutTimeout: Duration

  class DefaultOpenRequest(val request: HttpRequest,
                           private[this] val closeAfterResponseCompletion: Boolean,
                           private[this] var timestamp: Long) extends OpenRequest {
    private[this] val receiverRef = new ResponseReceiverRef(this)
    private[this] var handler = context.handler
    private[this] var nextInChain: OpenRequest = EmptyOpenRequest
    private[this] var responseQueue = Queue.empty[Command]
    private[this] var pendingSentAcks: Int = 1000 // we use an offset of 1000 for as long as the response is not finished

    def context: Context = component.context
    def isEmpty = false

    def appendToEndOfChain(openRequest: OpenRequest): OpenRequest = {
      nextInChain = nextInChain appendToEndOfChain openRequest
      this
    }

    def dispatchInitialRequestPartToHandler() {
      val requestToDispatch =
        if (request.method == HttpMethods.HEAD && settings.transparentHeadRequests)
          request.copy(method = HttpMethods.GET)
        else request
      val partToDispatch: HttpRequestPart =
        if (timestamp == 0L) ChunkedRequestStart(requestToDispatch)
        else requestToDispatch
      if (context.log.isDebugEnabled)
        context.log.debug("Dispatching {} to handler {}", format(partToDispatch), handler)
      downstreamCommandPL(Pipeline.Tell(handler, partToDispatch, receiverRef))
    }

    def dispatchNextQueuedResponse() {
      if (responsesQueued) {
        context.self.tell(responseQueue.head, handler)
        responseQueue = responseQueue.tail
      }
    }

    def checkForTimeout(now: Long) {
      if (timestamp > 0) {
        if (timestamp + requestTimeout.toMillis < now) {
          val timeoutHandler =
            if (settings.timeoutHandler.isEmpty) handler
            else context.actorContext.actorFor(settings.timeoutHandler)
          if (RefUtils.isLocal(timeoutHandler))
            downstreamCommandPL(Pipeline.Tell(timeoutHandler, Timedout(request), receiverRef))
          else context.log.warning("The TimeoutHandler '{}' is not a local actor and thus cannot be used as a " +
            "timeout handler", timeoutHandler)
          timestamp = -now // we record the time of the Timeout dispatch as negative timestamp value
        }
      } else if (timestamp < -1 && timeoutTimeout.isFinite() && (-timestamp + timeoutTimeout.toMillis < now)) {
        val response = timeoutResponse(request)
        // we always close the connection after a timeout-timeout
        sendPart(response.withHeaders(HttpHeaders.Connection("close") :: response.headers))
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
      handler = context.actorContext.sender // remember who to send Closed events to
      sendPart(part)
      pendingSentAcks -= 1000 // remove initial offset to signal that the last part has gone out
      nextInChain.dispatchNextQueuedResponse()
      nextInChain
    }

    def handleResponsePart(part: HttpMessagePartWrapper) {
      timestamp = 0L // disable request timeout checking once the first response part has come in
      handler = context.actorContext.sender // remember who to send Closed events to
      sendPart(part)
      dispatchNextQueuedResponse()
    }

    def enqueueCommand(command: Command) {
      responseQueue = responseQueue enqueue command
    }

    /***** EVENTS *****/

    def handleMessageChunk(chunk: MessageChunk) {
      if (nextInChain.isEmpty)
        downstreamCommandPL(Pipeline.Tell(handler, chunk, receiverRef))
      else
        // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
        nextInChain handleMessageChunk chunk
    }

    def handleChunkedMessageEnd(part: ChunkedMessageEnd) {
      if (nextInChain.isEmpty) {
        // only start request timeout checking after request has been completed
        timestamp = System.currentTimeMillis
        downstreamCommandPL(Pipeline.Tell(handler, part, receiverRef))
      } else
        // we accept non-tail recursion since HTTP pipeline depth is limited (and small)
        nextInChain handleChunkedMessageEnd part
    }

    def handleSentAckAndReturnNextUnconfirmed(ev: AckEventWithReceiver) = {
      downstreamCommandPL(Pipeline.Tell(ev.receiver, ev.ack, receiverRef))
      pendingSentAcks -= 1
      // drop this openRequest from the unconfirmed list if we have seen the SentAck for the final response part
      if (pendingSentAcks == 0) nextInChain else this
    }

    def handleClosed(ev: Http.ConnectionClosed) {
      downstreamCommandPL(Pipeline.Tell(handler, ev, receiverRef))
    }

    /***** PRIVATE *****/

    private def sendPart(part: HttpMessagePartWrapper) {
      val responsePart = part.messagePart.asInstanceOf[HttpResponsePart]
      val ack = part.ack match {
        case None ⇒ Tcp.NoAck(PartAndSender(responsePart, context.sender))
        case Some(x) ⇒
          pendingSentAcks += 1
          AckEventWithReceiver(x, handler)
      }
      val cmd = ResponsePartRenderingContext(responsePart, request.method, request.protocol,
        closeAfterResponseCompletion, ack)
      downstreamCommandPL(cmd)
    }

    private def responsesQueued = responseQueue != null && !responseQueue.isEmpty

    private def format(part: HttpMessagePart) = part match {
      case x: HttpRequestPart with HttpMessageStart ⇒
        val request = x.message.asInstanceOf[HttpRequest]
        request.method + " request to " + request.uri
      case MessageChunk(body, _) ⇒ body.length.toString + " byte request chunk"
      case x                     ⇒ x.toString
    }
  }

  object EmptyOpenRequest extends OpenRequest {
    def appendToEndOfChain(openRequest: OpenRequest) = openRequest
    def context: Context = component.context
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

    def handleClosed(ev: Http.ConnectionClosed) {
      downstreamCommandPL(Pipeline.Tell(context.handler, ev, context.self))
    }
  }

}

private[server] case class AckEventWithReceiver(ack: Any, receiver: ActorRef) extends Event
private[server] case class PartAndSender(part: HttpResponsePart, sender: ActorRef)
