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
import HttpHeaders._
import MediaTypes._
import HttpCharsets._
import scala.collection.JavaConversions._
import java.net.{UnknownHostException, InetAddress}
import org.parboiled.common.FileUtils
import collection.mutable.ListBuffer
import java.io.ByteArrayOutputStream
import utils.CantWriteResponseBodyException

/**
 * The logic for converting [[cc.spray.RawRequest]]s to [[cc.spray.http.HttpRequest]]s and
 * [[cc.spray.RawResponse]]s to [[cc.spray.http.HttpResponse]]s.
 * Separated out from the [[cc.spray.RootService]] actor for testability.
 */
trait ToFromRawConverter {
  
  def addConnectionCloseResponseHeader: Boolean
  
  protected[spray] def toSprayRequest(request: RawRequest): HttpRequest = {
    val (contentType, contentLength, headers) = buildHeaders(request.headers)
    HttpRequest(
      HttpMethods.getForKey(request.method).get,
      request.uri,
      headers,
      readContent(request, contentType, contentLength),
      getRemoteHost(request.remoteIP),
      HttpVersions.getForKey(request.protocol)
    )
  }

  protected def buildHeaders(headers: collection.Map[String, String]) = {
    var contentType: Option[`Content-Type`] = None
    var contentLength: Option[`Content-Length`] = None
    val filtered = ListBuffer.empty[HttpHeader]
    headers.foreach {
      case (name, value) => HttpHeader(name, value) match {
        case x:`Content-Type` => contentType = Some(x)
        case x:`Content-Length` => contentLength = Some(x)
        case x => filtered += x 
      } 
    }
    (contentType, contentLength, filtered.toList)
  }
  
  protected def readContent(request: RawRequest, contentTypeHeader: Option[`Content-Type`],
                            contentLengthHeader: Option[`Content-Length`]) = {
    contentLengthHeader.flatMap { contentLength => 
      // TODO: support chunked transfer encoding (which does not come with a content-length header)
      val buf = new ByteArrayOutputStream(contentLength.length)
      FileUtils.copyAll(request.inputStream, buf)
      val bytes = buf.toByteArray
      if (bytes.length > 0) {
        // so far we do not guess the content-type
        // see http://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1
        val contentType = contentTypeHeader.map(_.contentType).getOrElse(ContentType(`application/octet-stream`))
        Some(HttpContent(contentType, bytes))
      } else None
    }
  }
  
  protected def getRemoteHost(remoteIP: String) = {
    try {
      Some(HttpIp(InetAddress.getByName(remoteIP)))
    } catch {
      case _: UnknownHostException => None 
    }
  }
  
  protected[spray] def fromSprayResponse(response: HttpResponse): RawResponse => Unit = {
    raw => {
      raw.setStatus(response.status.code.value)
      response.headers.foreach(header => raw.addHeader(header.name, header.value))
      if (addConnectionCloseResponseHeader && !response.headers.exists(_.isInstanceOf[HttpHeaders.Connection])) {
        raw.addHeader("Connection", "close")
      }
      response.content match {
        case Some(content) => {
          raw.addHeader("Content-Length", content.buffer.length.toString)
          raw.addHeader("Content-Type", content.contentType.value)
          try {
            FileUtils.copyAll(content.inputStream, raw.outputStream)
          } catch {
            case e: Exception => throw new CantWriteResponseBodyException
          }
        }
        case None => 
      }
    }
  }
  
}