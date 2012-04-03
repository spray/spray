/*
 * Copyright (C) 2011 Mathias Doenitz
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
package connectors

import util.Logging
import collection.JavaConversions._        ¸
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import util.ActorHelpers._
import http._
import HttpHeaders._
import StatusCodes._
import MediaTypes._
import java.util.concurrent.{TimeUnit, CountDownLatch}
import java.io.{IOException, InputStream}

private[connectors] abstract class ConnectorServlet(containerName: String) extends HttpServlet with Logging {
  lazy val rootService = actor(SprayServerSettings.RootActorId)
  lazy val timeoutActor = actor(SprayServerSettings.TimeoutActorId)
  var timeout: Int = _

  override def init() {
    log.info("Initializing {} <=> Spray Connector", containerName)
    timeout = SprayServerSettings.RequestTimeout
    log.info("Async timeout for all requests is {} ms", timeout)
  }

  def requestContext(req: HttpServletRequest, resp: HttpServletResponse,
                     responder: RequestResponder): Option[RequestContext] = {
    try {
      Some {
        RequestContext(
          request = httpRequest(req),
          remoteHost = req.getRemoteAddr,
          responder = responder
        )
      }
    } catch {
      case HttpException(failure, reason) => respond(req, resp, HttpResponse(failure.value, reason)); None
      case e: Exception => respond(req, resp, HttpResponse(500, "Internal Server Error:\n" + e.toString)); None
    }
  }

  def httpRequest(req: HttpServletRequest) = {
    val (contentTypeHeader, contentLengthHeader, regularHeaders) = HttpHeaders.parseFromRaw {
      req.getHeaderNames.toList.map { name =>
        name -> req.getHeaders(name).toList.mkString(", ")
      }
    }
    HttpRequest(
      method = HttpMethods.getForKey(req.getMethod).get,
      uri = rebuildUri(req),
      headers = regularHeaders,
      content = httpContent(req.getInputStream, contentTypeHeader, contentLengthHeader),
      protocol = HttpProtocols.getForKey(req.getProtocol).get
    )
  }

  def rebuildUri(req: HttpServletRequest) = {
    val uri = req.getRequestURI
    val queryString = req.getQueryString
    if (queryString != null && queryString.length > 1) uri + '?' + queryString else uri
  }

  def httpContent(inputStream: InputStream, contentTypeHeader: Option[`Content-Type`],
                  contentLengthHeader: Option[`Content-Length`]): Option[HttpContent] = {
    contentLengthHeader.flatMap {
      case `Content-Length`(0) => None
      case `Content-Length`(contentLength) => {
        val body = if (contentLength == 0) util.EmptyByteArray else try {
          val buf = new Array[Byte](contentLength)
          var bytesRead = 0
          while (bytesRead < contentLength) {
            val count = inputStream.read(buf, bytesRead, contentLength - bytesRead)
            if (count >= 0) bytesRead += count
            else throw new HttpException(BadRequest, "Illegal Servlet request entity, expected length " +
                    contentLength + " but only has length " + bytesRead)
          }
          buf
        } catch {
          case e: IOException =>
            throw new HttpException(InternalServerError, "Could not read request entity due to " + e.toString)
        }
        val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
        Some(HttpContent(contentType, body))
      }
    }
  }

  def respond(req: HttpServletRequest, servletResponse: HttpServletResponse, response: HttpResponse) {
    try {
      servletResponse.setStatus(response.status.value)
      response.headers.foreach(header => servletResponse.addHeader(header.name, header.value))
      response.content.foreach { content =>
        servletResponse.addHeader("Content-Type", content.contentType.value)
        servletResponse.addHeader("Content-Length", content.buffer.length.toString)
        servletResponse.getOutputStream.write(content.buffer)
      }
    } catch {
      case e: IOException => log.error("Could not write response body of {}, probably the request has either timed out" +
        "or the client has disconnected ({})", requestString(req), e)
      case e: Exception => log.error(e, "Could not complete {}", requestString(req))
    }
  }

  def responderFor(req: HttpServletRequest)(f: HttpResponse => Unit): RequestResponder = {
    RequestResponder(
      complete = { response =>
        try {
          f(response)
        } catch {
          case e: IllegalStateException => log.error("Could not complete {}, it probably timed out and has therefore" +
            "already been completed ({})", requestString(req), e)
          case e: Exception => log.error("Could not complete {} due to {}", requestString(req), e)
        }
      },
      reject = _ => throw new IllegalStateException
    )
  }

  def handleTimeout(req: HttpServletRequest, resp: HttpServletResponse)(complete: => Unit) {
    val latch = new CountDownLatch(1);
    val responder = responderFor(req) { response =>
      respond(req, resp, response)
      complete
      latch.countDown()
    }
    requestContext(req, resp, responder).foreach { context =>
      log.error("Timeout of {}", context.request)
      timeoutActor ! Timeout(context)
      latch.await(timeout, TimeUnit.MILLISECONDS) // give the timeoutActor another `timeout` ms for completing
    }
  }

  def requestString(req: HttpServletRequest) = req.getMethod + " request to '" + rebuildUri(req) + "'"

}