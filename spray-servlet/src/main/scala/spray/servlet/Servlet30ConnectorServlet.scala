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

package spray.servlet

import java.io.IOException
import java.util.concurrent.{TimeUnit, CountDownLatch}
import javax.servlet.{AsyncEvent, AsyncListener}
import javax.servlet.http.{HttpServlet, HttpServletResponse, HttpServletRequest}
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{UnhandledMessage, ActorRef, ActorSystem}
import akka.util.NonFatal
import akka.spray.UnregisteredActorRef
import spray.http._
import spray.util._


/**
 * The connector servlet for all servlet 3.0 containers.
 */
class Servlet30ConnectorServlet extends HttpServlet {
  var system: ActorSystem = _
  var serviceActor: ActorRef = _
  var timeoutHandler: ActorRef = _
  implicit var settings: ConnectorSettings = _
  implicit def log = system.log

  override def init() {
    import Initializer._
    system = getServletContext.getAttribute(SystemAttrName).asInstanceOf[ActorSystem]
    serviceActor = getServletContext.getAttribute(ServiceActorAttrName).asInstanceOf[ActorRef]
    settings = getServletContext.getAttribute(SettingsAttrName).asInstanceOf[ConnectorSettings]
    timeoutHandler = if (settings.TimeoutHandler.isEmpty) serviceActor else system.actorFor(settings.TimeoutHandler)
    require(system != null, "No ActorSystem configured")
    require(serviceActor != null, "No ServiceActor configured")
    require(settings != null, "No ConnectorSettings configured")
    log.info("Initialized Servlet API 3.0 <=> Spray Connector")
  }

  override def service(hsRequest: HttpServletRequest, hsResponse: HttpServletResponse) {
    def request = "%s request to '%s'" format (hsRequest.getMethod, ModelConverter.rebuildUri(hsRequest))
    try {
      val request = ModelConverter.toHttpRequest(hsRequest)
      val responder = new Responder(hsRequest, hsResponse, request)
      serviceActor.tell(request, responder)
    } catch {
      case IllegalRequestException(status, summary, detail) =>
        log.warning("Illegal request {}\n\t{}: {}\n\tCompleting with '{}' response", request, summary, detail, status)
        val msg = if (settings.VerboseErrorMessages) summary + ": " + detail else summary
        writeResponse(HttpResponse(status, msg), hsResponse, request) {}
      case RequestProcessingException(status, msg) =>
        log.warning("Request {} could not be handled normally\n\t{}\n\tCompleting with '{}' response", request, msg, status)
        writeResponse(HttpResponse(status, msg), hsResponse, request) {}
      case NonFatal(e) =>
        log.error(e, "Error during processing of request {}", request)
        writeResponse(HttpResponse(500, entity = "The request could not be handled"), hsResponse, request) {}
    }
  }

  class Responder(hsRequest: HttpServletRequest, hsResponse: HttpServletResponse, req: HttpRequest)
    extends UnregisteredActorRef(system) {

    val OPEN = 0
    val STARTED = 1
    val COMPLETED = 2

    val state = new AtomicInteger(OPEN)
    val asyncContext = hsRequest.startAsync()
    asyncContext.setTimeout(settings.RequestTimeout)
    asyncContext.addListener {
      new AsyncListener {
        def onTimeout(event: AsyncEvent) {
          handleTimeout(hsResponse, req)
          asyncContext.complete()
        }
        def onError(event: AsyncEvent) {
          event.getThrowable match {
            case null => log.error("Unspecified Error during async processing of {}", req)
            case ex => log.error(ex, "Error during async processing of {}", req)
          }
        }
        def onStartAsync(event: AsyncEvent) {}
        def onComplete(event: AsyncEvent) {}
      }
    }

    def postProcess(error: Option[Throwable], sentAck: Option[Any], close: Boolean)(implicit sender: ActorRef) {
      error match {
        case None =>
          sentAck.foreach(sender.tell(_, this))
          if (close) sender.tell(Closed(CleanClose), this)
        case Some(e) =>
          sender.tell(Closed(IOError(e)), this)
          asyncContext.complete()
      }
    }

    def handle(message: Any)(implicit sender: ActorRef) {
      message match {
        case wrapper: HttpMessagePartWrapper if wrapper.messagePart.isInstanceOf[HttpResponsePart] =>
          wrapper.messagePart.asInstanceOf[HttpResponsePart] match {
            case response: HttpResponse =>
              if (state.compareAndSet(OPEN, COMPLETED)) {
                val error = writeResponse(response, hsResponse, req) { asyncContext.complete() }
                postProcess(error, wrapper.sentAck, close = true)
              } else state.get match {
                case STARTED =>
                  log.warning("Received an HttpResponse after a ChunkedResponseStart, dropping ...\nRequest: {}\nResponse: {}", req, response)
                case COMPLETED =>
                  log.warning("Received a second response for a request that was already completed, dropping ...\nRequest: {}\nResponse: {}", req, response)
              }

            case response: ChunkedResponseStart =>
              if (state.compareAndSet(OPEN, STARTED)) {
                val error = writeResponse(response, hsResponse, req) {}
                postProcess(error, wrapper.sentAck, close = false)
              } else state.get match {
                case STARTED =>
                  log.warning("Received a second ChunkedResponseStart, dropping ...\nRequest: {}\nResponse: {}", req, response)
                case COMPLETED =>
                  log.warning("Received a ChunkedResponseStart for a request that was already completed, dropping ...\nRequest: {}\nResponse: {}", req, response)
              }

            case MessageChunk(body, _) => state.get match {
              case OPEN =>
                log.warning("Received a MessageChunk before a ChunkedResponseStart, dropping ...\nRequest: {}\nChunk: {} bytes\n", req, body.length)
              case STARTED =>
                val error = writeChunk(body, hsResponse, req)
                postProcess(error, wrapper.sentAck, close = false)
              case COMPLETED =>
                log.warning("Received a MessageChunk for a request that was already completed, dropping ...\nRequest: {}\nChunk: {} bytes", req, body.length)
            }

            case _: ChunkedMessageEnd =>
              if (state.compareAndSet(STARTED, COMPLETED)) {
                val error = closeResponseStream(hsResponse, req) { asyncContext.complete() }
                postProcess(error, wrapper.sentAck, close = true)
              } else state.get match {
                case OPEN =>
                  log.warning("Received a ChunkedMessageEnd before a ChunkedResponseStart, dropping ...\nRequest: {}", req)
                case COMPLETED =>
                  log.warning("Received a ChunkedMessageEnd for a request that was already completed, dropping ...\nRequest: {}", req)
              }
          }

        case x => system.eventStream.publish(UnhandledMessage(x, sender, this))
      }
    }
  }

  def handleTimeout(hsResponse: HttpServletResponse, req: HttpRequest) {
    log.warning("Timeout of {}", req)
    val latch = new CountDownLatch(1)
    val responder = new UnregisteredActorRef(system) {
      def handle(message: Any)(implicit sender: ActorRef) {
        message match {
          case x: HttpResponse => writeResponse(x, hsResponse, req) { latch.countDown() }
          case x => system.eventStream.publish(UnhandledMessage(x, sender, this))
        }
      }
    }
    timeoutHandler.tell(Timeout(req), responder)
    // we need to react synchronously to Timeout events (thx to the great Servlet API design), so we block here
    latch.await(settings.TimeoutTimeout, TimeUnit.MILLISECONDS)
    if (latch.getCount != 0) writeResponse(timeoutResponse(req), hsResponse, req) {}
  }

  def writeResponse(response: HttpMessageStart with HttpResponsePart,
                    hsResponse: HttpServletResponse, req: AnyRef)(complete: => Unit): Option[Throwable] = {
    try {
      val resp = response.message.asInstanceOf[HttpResponse]
      hsResponse.setStatus(resp.status.value)
      resp.headers.foreach { header =>
        header.lowercaseName match {
          case "content-type"   => // we never render these headers here, because their production is the
          case "content-length" => // responsibility of the spray-servlet layer, not the user
          case _ => hsResponse.addHeader(header.name, header.value)
        }
      }
      resp.entity.foreach { (contentType, buffer) =>
        hsResponse.addHeader("Content-Type", contentType.value)
        if (response.isInstanceOf[HttpResponse]) hsResponse.addHeader("Content-Length", buffer.length.toString)
        hsResponse.getOutputStream.write(buffer)
        hsResponse.getOutputStream.flush()
      }
      complete
      None
    } catch {
      case e: IOException =>
        log.error("Could not write response body, probably the request has either timed out or the client has " +
          "disconnected\nRequest: {}\nResponse: {}\nError: {}", req, response, e)
        Some(e)
      case NonFatal(e) =>
        log.error("Could not complete request\nRequest: {}\nResponse: {}\nError: {}", req, response, e)
        Some(e)
    }
  }

  def writeChunk(buffer: Array[Byte], hsResponse: HttpServletResponse, req: HttpRequest): Option[Throwable] = {
    try {
      hsResponse.getOutputStream.write(buffer)
      hsResponse.getOutputStream.flush()
      None
    }
    catch {
      case e: IOException =>
        log.error("Could not write response chunk, probably the request has either timed out or the client has " +
          "disconnected\nRequest: {}\nChunk: {} bytes\nError: {}", req, buffer.length, e)
        Some(e)
      case NonFatal(e) =>
        log.error("Could not write response chunk\nRequest: {}\nChunk: {} bytes\nError: {}", req, buffer.length, e)
        Some(e)
    }
  }

  def closeResponseStream(hsResponse: HttpServletResponse, req: HttpRequest)(complete: => Unit): Option[Throwable] = {
    try {
      complete
      None
    } catch {
      case e: IOException =>
        log.error("Could not close response stream, probably the request has either timed out or the client has " +
          "disconnected\nRequest: {}\nError: {}", req, e)
        Some(e)
      case NonFatal(e) =>
        log.error("Could not close response stream\nRequest: {}\nError: {}", req, e)
        Some(e)
    }
  }

  def timeoutResponse(request: HttpRequest): HttpResponse = HttpResponse(
    status = 500,
    entity = "Ooops! The server was not able to produce a timely response to your request.\n" +
      "Please try again in a short while!"
  )

  case class Closed(reason: ConnectionClosedReason) extends IOClosed
}

