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
import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConverters._
import akka.event.LoggingAdapter
import spray.util.EmptyByteArray
import spray.http.parser.HttpParser
import spray.http._
import HttpHeaders._
import StatusCodes._
import javax.servlet.ServletInputStream
import scala.annotation.tailrec

object ModelConverter {

  def toHttpRequest(hsRequest: HttpServletRequest)(implicit settings: ConnectorSettings, log: LoggingAdapter): HttpRequest = {
    val rawHeaders = hsRequest.getHeaderNames.asScala.map { name ⇒
      RawHeader(name, hsRequest.getHeaders(name).asScala mkString ", ")
    }.toList
    val (errors, parsedHeaders) = HttpParser.parseHeaders(rawHeaders)
    if (!errors.isEmpty) errors.foreach(e ⇒ log.warning(e.formatPretty))
    val (contentType, contentLength) = parsedHeaders.foldLeft[(Option[ContentType], Option[Int])](None -> None) {
      case ((None, cl), `Content-Type`(ct))   ⇒ Some(ct) -> cl
      case ((ct, None), `Content-Length`(cl)) ⇒ ct -> Some(cl)
      case (result, _)                        ⇒ result
    }

    HttpRequest(
      method = toHttpMethod(hsRequest.getMethod),
      uri = rebuildUri(hsRequest),
      headers = addRemoteAddressHeader(hsRequest, rawHeaders),
      entity = toHttpEntity(hsRequest, contentType, contentLength),
      protocol = toHttpProtocol(hsRequest.getProtocol))
  }

  def toHttpMethod(name: String) =
    HttpMethods.getForKey(name)
      .getOrElse(throw new IllegalRequestException(MethodNotAllowed, ErrorInfo("Illegal HTTP method", name)))

  def rebuildUri(hsRequest: HttpServletRequest)(implicit settings: ConnectorSettings, log: LoggingAdapter) = {
    val requestUri = hsRequest.getRequestURI
    val uri = settings.rootPath match {
      case ""                                         ⇒ requestUri
      case rootPath if requestUri startsWith rootPath ⇒ requestUri substring rootPath.length
      case rootPath ⇒
        log.warning("Received request outside of configured root-path, request uri '{}', configured root path '{}'",
          requestUri, rootPath)
        requestUri
    }
    val queryString = hsRequest.getQueryString
    try Uri(if (queryString != null && queryString.length > 0) uri + '?' + queryString else uri)
    catch {
      case e: IllegalUriException ⇒
        throw new IllegalRequestException(BadRequest, ErrorInfo("Illegal request URI", e.getMessage))
    }
  }

  def addRemoteAddressHeader(hsr: HttpServletRequest, headers: List[HttpHeader])(implicit settings: ConnectorSettings): List[HttpHeader] =
    if (settings.remoteAddressHeader) `Remote-Address`(hsr.getRemoteAddr) :: headers
    else headers

  def toHttpProtocol(name: String) =
    HttpProtocols.getForKey(name)
      .getOrElse(throw new IllegalRequestException(BadRequest, ErrorInfo("Illegal HTTP protocol", name)))

  def toHttpEntity(hsRequest: HttpServletRequest, contentType: Option[ContentType], contentLength: Option[Int])(implicit settings: ConnectorSettings, log: LoggingAdapter): HttpEntity = {
    @tailrec
    def drainRequestInputStream(buf: Array[Byte], inputStream: ServletInputStream, bytesRead: Int = 0): Array[Byte] =
      if (bytesRead < contentLength.get) {
        val count = inputStream.read(buf, bytesRead, contentLength.get - bytesRead)
        if (count >= 0) drainRequestInputStream(buf, inputStream, bytesRead + count)
        else throw new RequestProcessingException(InternalServerError, "Illegal Servlet request entity, " +
          "expected length " + contentLength + " but only has length " + bytesRead)
      }
      else buf

    val body =
      if (contentLength.isDefined && contentLength.get > 0) {
        if (contentLength.get <= settings.maxContentLength) {
          try drainRequestInputStream(new Array[Byte](contentLength.get), hsRequest.getInputStream)
          catch {
            case e: IOException ⇒
              log.error(e, "Could not read request entity")
              throw new RequestProcessingException(InternalServerError, "Could not read request entity")
          }
        }
        else throw new IllegalRequestException(RequestEntityTooLarge, ErrorInfo("HTTP message Content-Length " +
          contentLength.get + " exceeds the configured limit of " + settings.maxContentLength))
      }
      else EmptyByteArray
    if (contentType.isEmpty) HttpEntity(body) else HttpBody(contentType.get, body)
  }

}