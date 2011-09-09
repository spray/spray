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

import utils.Logging
import collection.JavaConversions._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import utils.ActorHelpers._
import http._
import HttpHeaders._
import StatusCodes._
import MediaTypes._
import java.io.{IOException, InputStream}

private[connectors] abstract class ConnectorServlet(containerName: String) extends HttpServlet with Logging {
  lazy val rootService = actor(SpraySettings.RootActorId)
  lazy val timeoutActor = actor(SpraySettings.TimeoutActorId)
  var timeout: Int = _
  val EmptyResponder: RoutingResult => Unit = { _ => }

  override def init() {
    log.info("Initializing %s <=> Spray Connector", containerName)
    timeout = SpraySettings.AsyncTimeout
    log.info("Async timeout for all requests is %s ms", timeout)
  }

  def requestContext(req: HttpServletRequest, resp: HttpServletResponse,
                     responderFactory: RequestContext => RoutingResult => Unit): Option[RequestContext] = {
    try {
      val context = RequestContext(
        request = httpRequest(req),
        remoteHost = req.getRemoteAddr,
        responder = EmptyResponder
      )
      Some(context.withResponder(responderFactory(context)))
    } catch {
      case HttpException(failure, reason) => respond(resp, HttpResponse(failure.value, reason)); None
      case e: Exception => respond(resp, HttpResponse(500, "Internal Server Error:\n" + e.toString)); None
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
    val buffer = req.getRequestURL
    val queryString = req.getQueryString
    if (queryString != null && queryString.length > 1) buffer.append('?').append(queryString)
    buffer.toString
  }

  def httpContent(inputStream: InputStream, contentTypeHeader: Option[`Content-Type`],
                  contentLengthHeader: Option[`Content-Length`]): Option[HttpContent] = {
    contentLengthHeader.flatMap {
      case `Content-Length`(0) => None
      case `Content-Length`(contentLength) => {
        val buf = new Array[Byte](contentLength)
        if (inputStream.read(buf) != contentLength) throw new HttpException(BadRequest, "Illegal Servlet request content")
        val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
        Some(HttpContent(contentType, buf))
      }
    }
  }

  def respond(servletResponse: HttpServletResponse, response: HttpResponse) {
    try {
      servletResponse.setStatus(response.status.value)
      response.headers.foreach(header => servletResponse.addHeader(header.name, header.value))
      response.content.foreach { content =>
        servletResponse.addHeader("Content-Type", content.contentType.value)
        servletResponse.addHeader("Content-Length", content.buffer.length.toString)
        servletResponse.getOutputStream.write(content.buffer)
      }
    } catch {
      case e: IOException => log.error("Could not write response body, " +
                "probably the request has either timed out or the client has disconnected")
      case e: Exception => log.error(e, "Could not complete request")
    }
  }

  def responder(f: HttpResponse => Unit): RoutingResult => Unit = {
    case Respond(response) => f(response)
    case _: Reject => throw new IllegalStateException
  }

}