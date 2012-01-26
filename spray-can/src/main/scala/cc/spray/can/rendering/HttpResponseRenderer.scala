/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can
package rendering

import java.lang.{StringBuilder => JStringBuilder}
import java.nio.ByteBuffer
import model._
import HttpProtocols._
import util.DateTime

class HttpResponseRenderer(serverHeader: String) {
  import HttpMessageRendering._

  private val serverHeaderPlusDateColonSP =
    if (serverHeader.isEmpty) "Date: " else "Server: " + serverHeader + "\r\nDate: "

  def renderResponse(requestLine: RequestLine, response: HttpResponse,
                     requestConnectionHeader: Option[String]): RenderedHttpResponse = {
    import response._
    val (sb, close) = renderResponseStart(requestLine, response, requestConnectionHeader)
    // don't set a Content-Length header for non-keepalive HTTP/1.0 responses (rely on body end by connection close)
    if (response.protocol == `HTTP/1.1` || !close) appendHeader("Content-Length", body.length.toString, sb)
    appendLine(sb)
    val bodyBufs = if (body.length == 0 || requestLine.method == HttpMethods.HEAD) Nil else ByteBuffer.wrap(body) :: Nil
    RenderedHttpResponse(encode(sb) :: bodyBufs, close)
  }

  def renderChunkedResponseStart(requestLine: RequestLine, response: HttpResponse,
                                 requestConnectionHeader: Option[String]): RenderedHttpResponse = {
    import response._
    val (sb, close) = renderResponseStart(requestLine, response, requestConnectionHeader)
    appendHeader("Transfer-Encoding", "chunked", sb)
    appendLine(sb)
    val bodyBufs = if (body.length == 0 || requestLine.method == HttpMethods.HEAD) Nil else prepareChunk(Nil, body)
    RenderedHttpResponse(encode(sb) :: bodyBufs, close)
  }

  private def renderResponseStart(requestLine: RequestLine, response: HttpResponse,
                                  requestConnectionHeader: Option[String]) = {
    import response._

    def appendConnectionHeaderIfRequired(connectionHeaderValue: Option[String], sb: JStringBuilder) = {
      requestLine.protocol match {
        case `HTTP/1.0` => {
          if (connectionHeaderValue.isEmpty) {
            if (requestConnectionHeader.isDefined && requestConnectionHeader.get == "Keep-Alive") {
              appendHeader("Connection", "Keep-Alive", sb)
              false
            } else true
          } else !connectionHeaderValue.get.contains("Keep-Alive")
        }
        case `HTTP/1.1` => {
          if (connectionHeaderValue.isEmpty) {
            if (requestConnectionHeader.isDefined && requestConnectionHeader.get == "close") {
              if (response.protocol == `HTTP/1.1`) appendHeader("Connection", "close", sb)
              true
            } else response.protocol == `HTTP/1.0`
          } else connectionHeaderValue.get.contains("close")
        }
      }
    }

    val sb = new JStringBuilder(256)
    if (status == 200 && protocol == `HTTP/1.1`) {
      sb.append("HTTP/1.1 200 OK\r\n")
    } else appendLine {
      sb.append(protocol.name).append(' ').append(status).append(' ').append(HttpResponse.defaultReason(status))
    }
    val connectionHeaderValue = appendHeaders(headers, sb)
    val close = appendConnectionHeaderIfRequired(connectionHeaderValue, sb)
    appendLine(sb.append(serverHeaderPlusDateColonSP).append(dateTimeNow.toRfc1123DateTimeString))
    (sb, close)
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}

case class RenderedHttpResponse(buffers: List[ByteBuffer], closeConnection: Boolean)