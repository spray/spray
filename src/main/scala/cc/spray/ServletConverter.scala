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

import http._
import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils
import java.net.{UnknownHostException, InetAddress}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import HttpHeaders._
import MediaTypes._
import Charsets._

/**
 * The logic for converting
 * [[http://download.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html HttpServletRequests]]
 * to [[HttpRequest]]s and
 * [[http://download.oracle.com/javaee/6/api/javax/servlet/http/HttpServletResponse.html HttpServletResponses]]
 * to [[HttpResponse]]s.
 * Separated out from the [[RootService]] actor for testability.
 */
trait ServletConverter {
  
  protected[spray] def toSprayRequest(request: HttpServletRequest): HttpRequest = {
    val (ctHeaders, headers) = buildHeaders(request).partition(_.isInstanceOf[`Content-Type`])
    HttpRequest(
      HttpMethods.getForKey(request.getMethod).get,
      getRequestUri(request),
      headers,
      readContent(request, ctHeaders.headOption.asInstanceOf[Option[`Content-Type`]]),
      getRemoteHost(request),
      HttpVersions.getForKey(request.getProtocol)
    )
  }

  protected def buildHeaders(request: HttpServletRequest): List[HttpHeader] = {
    for (
      name <- request.getHeaderNames.asInstanceOf[java.util.Enumeration[String]].toList;
      value <- request.getHeaders(name).asInstanceOf[java.util.Enumeration[String]].toList
    ) yield {
      HttpHeader(name, value)
    }
  }
  
  protected def getRequestUri(request: HttpServletRequest) = {
    val buffer = request.getRequestURL
    val queryString = request.getQueryString
    if (queryString != null && queryString.length > 1) buffer.append('?').append(queryString)
    buffer.toString
  }

  protected def readContent(request: HttpServletRequest, header: Option[`Content-Type`]): Option[HttpContent] = {
    val bytes = IOUtils.toByteArray(request.getInputStream)
    if (bytes.length > 0) {
      // so far we do not guess the content-type
      // see http://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1
      val contentType = header.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
      Some(HttpContent(contentType, bytes))
    } else {
      None
    }
  }
  
  protected def getRemoteHost(request: HttpServletRequest) = {
    try {
      Some(HttpIp(InetAddress.getByName(request.getRemoteAddr)))
    } catch {
      case _: UnknownHostException => None 
    }
  }
  
  protected[spray] def fromSprayResponse(response: HttpResponse): HttpServletResponse => Unit = {
    hsr => {
      hsr.setStatus(response.status.code.value)
      for (HttpHeader(name, value) <- response.headers) {
        if (name == "Content-Type") {
          // TODO: move higher up
          throw new RuntimeException("HttpResponse must not include explicit Content-Type header")
        }
        hsr.setHeader(name, value)
      }
      response.content match {
        case Some(buffer) => {
          hsr.setContentLength(buffer.length)
          hsr.setContentType(buffer.contentType.value)
          IOUtils.copy(buffer.inputStream, hsr.getOutputStream)
        }
        case None => if (!response.isSuccess) {
          hsr.setContentType("text/plain")
          hsr.getWriter.write(response.status.reason)
          hsr.getWriter.close()
        }
      }
      hsr.flushBuffer()
    }
  }
  
}