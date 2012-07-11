/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.servlet

import java.io.IOException
import javax.servlet.http.HttpServletRequest
import cc.spray.util.EmptyByteArray
import cc.spray.http.parser.HttpParser
import cc.spray.util._
import cc.spray.http._
import HttpHeaders._
import StatusCodes._


object ModelConverter {

  def toHttpRequest(hsRequest: HttpServletRequest)(implicit settings: ConnectorSettings): HttpRequest = {
    import collection.JavaConverters._
    var contentType: ContentType = null
    var contentLength: Int = 0
    val rawHeaders = hsRequest.getHeaderNames.asScala.toList.map { name =>
      val value = hsRequest.getHeaders(name).asScala.mkString(", ")
      val lcName = name.toLowerCase
      lcName match {
        case "content-type" =>
          contentType = HttpParser.parseContentType(value).fold(e => fail("Illegal Content-Type: " + e), identityFunc)
        case "content-length" =>
          contentLength = try value.toInt catch { case e: NumberFormatException => fail("Illegal Content-Length: ", e) }
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
    HttpMethods.getForKey(name).getOrElse(fail("Illegal HTTP method: " + name))

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
    HttpProtocols.getForKey(name).getOrElse(fail("Illegal HTTP protocol: " + name))

  def toHttpEntity(hsRequest: HttpServletRequest, contentType: ContentType, contentLength: Int): HttpEntity = {
    def body: Array[Byte] = {
      if (contentLength > 0) {
        try {
          val buf = new Array[Byte](contentLength)
          val inputStream = hsRequest.getInputStream
          var bytesRead = 0
          while (bytesRead < contentLength) {
            val count = inputStream.read(buf, bytesRead, contentLength - bytesRead)
            if (count >= 0) bytesRead += count
            else fail("Illegal Servlet request entity, expected length " + contentLength + " but only has length " + bytesRead)
          }
          buf
        } catch {
          case e: IOException => fail("Could not read request entity due to ", e, InternalServerError)
        }
      } else EmptyByteArray
    }
    if (contentType == null) HttpEntity(body) else HttpBody(contentType, body)
  }

  private def fail(message: String, inner: Exception = null, status: StatusCode = BadRequest) =
    throw HttpException(status, if (inner != null) message + inner.getMessage else message)

}