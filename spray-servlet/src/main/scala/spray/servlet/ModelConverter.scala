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

package spray.servlet

import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.ServletInputStream
import scala.annotation.tailrec
import akka.event.LoggingAdapter
import spray.http.parser.HttpParser
import spray.util._
import spray.http._
import HttpHeaders._
import StatusCodes._

object ModelConverter {

  def toHttpRequest(hsRequest: HttpServletRequest)(implicit settings: ConnectorSettings, log: LoggingAdapter): HttpRequest = {
    val (errors, parsedHeaders) = HttpParser.parseHeaders(rawHeaders(hsRequest))
    if (!errors.isEmpty) errors.foreach(e ⇒ log.warning(e.formatPretty))
    val contentType = parsedHeaders.collectFirst { case `Content-Type`(ct) ⇒ ct }
    HttpRequest(
      method = toHttpMethod(hsRequest.getMethod),
      uri = rebuildUri(hsRequest),
      headers = addRemoteAddressHeader(hsRequest, parsedHeaders),
      entity = toHttpEntity(hsRequest, contentType, hsRequest.getContentLength),
      protocol = toHttpProtocol(hsRequest.getProtocol))
  }

  def rawHeaders(hsRequest: HttpServletRequest): List[RawHeader] = {
    @tailrec def rec(names: java.util.Enumeration[String], headers: List[RawHeader] = Nil): List[RawHeader] =
      if (names.hasMoreElements) {
        val name = names.nextElement()
        @tailrec def concatValues(values: java.util.Enumeration[String], sb: java.lang.StringBuilder): String =
          if (values.hasMoreElements) concatValues(values,
            if (sb.length == 0) sb.append(values.nextElement()) else sb.append(", ").append(values.nextElement()))
          else sb.toString
        rec(names, RawHeader(name, concatValues(hsRequest.getHeaders(name), new java.lang.StringBuilder)) :: headers)
      } else headers
    rec(hsRequest.getHeaderNames)
  }

  def toHttpMethod(name: String) =
    HttpMethods.getForKey(name)
      .getOrElse(throw new IllegalRequestException(MethodNotAllowed, ErrorInfo("Illegal HTTP method", name)))

  def rebuildUri(hsRequest: HttpServletRequest)(implicit settings: ConnectorSettings, log: LoggingAdapter): Uri = {
    val buffer = hsRequest.getRequestURL
    hsRequest.getQueryString match {
      case null ⇒
      case x    ⇒ buffer.append('?').append(x)
    }
    try {
      val uri = Uri(buffer.toString)
      if (settings.rootPath.isEmpty) uri
      else if (uri.path.startsWith(settings.rootPath)) uri.copy(path = uri.path.dropChars(settings.rootPathCharCount))
      else {
        log.warning("Received request outside of configured root-path, request uri '{}', configured root path '{}'",
          uri, settings.rootPath)
        uri
      }
    } catch {
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

  def toHttpEntity(hsRequest: HttpServletRequest, contentType: Option[ContentType], contentLength: Int)(implicit settings: ConnectorSettings, log: LoggingAdapter): HttpEntity = {
    @tailrec
    def drainRequestInputStream(buf: Array[Byte], inputStream: ServletInputStream, bytesRead: Int = 0): Array[Byte] =
      if (bytesRead < contentLength) {
        val count = inputStream.read(buf, bytesRead, contentLength - bytesRead)
        if (count >= 0) drainRequestInputStream(buf, inputStream, bytesRead + count)
        else throw new RequestProcessingException(InternalServerError, "Illegal Servlet request entity, " +
          "expected length " + contentLength + " but only has length " + bytesRead)
      } else buf

    val body =
      if (contentLength > 0) {
        if (contentLength <= settings.maxContentLength) {
          try drainRequestInputStream(new Array[Byte](contentLength), hsRequest.getInputStream)
          catch {
            case e: IOException ⇒
              log.error(e, "Could not read request entity")
              throw new RequestProcessingException(InternalServerError, "Could not read request entity")
          }
        } else throw new IllegalRequestException(RequestEntityTooLarge, ErrorInfo("HTTP message Content-Length " +
          contentLength + " exceeds the configured limit of " + settings.maxContentLength))
      } else EmptyByteArray
    if (contentType.isEmpty) HttpEntity(body) else HttpEntity(contentType.get, body)
  }

}