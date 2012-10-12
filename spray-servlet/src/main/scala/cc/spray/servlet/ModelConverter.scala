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
import akka.event.LoggingAdapter
import spray.util.EmptyByteArray
import spray.http.parser.HttpParser
import spray.http._
import HttpHeaders._
import StatusCodes._


object ModelConverter {

  def toHttpRequest(hsRequest: HttpServletRequest)
                   (implicit settings: ConnectorSettings, log: LoggingAdapter): HttpRequest = {
    import collection.JavaConverters._
    var contentType: ContentType = null
    var contentLength: Int = 0
    val rawHeaders = hsRequest.getHeaderNames.asScala.toList.map { name =>
      val value = hsRequest.getHeaders(name).asScala.mkString(", ")
      val lcName = name.toLowerCase
      lcName match {
        case "content-type" =>
          contentType = HttpParser.parseContentType(value) match {
            case Right(x) => x
            case Left(errorInfo) => throw new IllegalRequestException(BadRequest, errorInfo)
          }
        case "content-length" =>
          contentLength =
            try value.toInt
            catch {
              case e: NumberFormatException =>
                throw new IllegalRequestException(BadRequest, RequestErrorInfo("Illegal Content-Length", e.getMessage))
            }
        case _ =>
      }
      RawHeader(lcName, value)
    }
    HttpRequest(
      method = toHttpMethod(hsRequest.getMethod),
      uri = rebuildUri(hsRequest),
      headers = addRemoteAddressHeader(hsRequest, rawHeaders),
      entity = toHttpEntity(hsRequest, contentType, contentLength),
      protocol = toHttpProtocol(hsRequest.getProtocol)
    )
  }

  def toHttpMethod(name: String) =
    HttpMethods.getForKey(name)
      .getOrElse(throw new IllegalRequestException(MethodNotAllowed, RequestErrorInfo("Illegal HTTP method", name)))

  def rebuildUri(hsRequest: HttpServletRequest) = {
    val uri = hsRequest.getRequestURI
    val queryString = hsRequest.getQueryString
    if (queryString != null && queryString.length > 0) uri + '?' + queryString else uri
  }

  def addRemoteAddressHeader(hsr: HttpServletRequest, headers: List[HttpHeader])
                            (implicit settings: ConnectorSettings): List[HttpHeader] = {
    if (settings.RemoteAddressHeader) `Remote-Address`(hsr.getRemoteAddr) :: headers
    else headers
  }

  def toHttpProtocol(name: String) =
    HttpProtocols.getForKey(name)
      .getOrElse(throw new IllegalRequestException(BadRequest, "Illegal HTTP protocol", name))

  def toHttpEntity(hsRequest: HttpServletRequest, contentType: ContentType, contentLength: Int)
                  (implicit settings: ConnectorSettings, log: LoggingAdapter): HttpEntity = {
    def body: Array[Byte] = {
      if (contentLength > 0) {
        if (contentLength <= settings.MaxContentLength) {
          try {
            val buf = new Array[Byte](contentLength)
            val inputStream = hsRequest.getInputStream
            var bytesRead = 0
            while (bytesRead < contentLength) {
              val count = inputStream.read(buf, bytesRead, contentLength - bytesRead)
              if (count >= 0) bytesRead += count
              else throw new RequestProcessingException(InternalServerError, "Illegal Servlet request entity, " +
                "expected length " + contentLength + " but only has length " + bytesRead)
            }
            buf
          } catch {
            case e: IOException =>
              log.error(e, "Could not read request entity")
              throw new RequestProcessingException(InternalServerError, "Could not read request entity")
          }
        } else throw new IllegalRequestException(RequestEntityTooLarge, "HTTP message Content-Length " +
          contentLength + " exceeds the configured limit of " + settings.MaxContentLength)
      } else EmptyByteArray
    }
    if (contentType == null) HttpEntity(body) else HttpBody(contentType, body)
  }

}