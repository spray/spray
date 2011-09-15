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
package cc.spray.can

import java.nio.ByteBuffer
import annotation.tailrec
import utils.DateTime
import java.nio.charset.Charset
import HttpProtocols._
import java.lang.{StringBuilder => JStringBuilder}

trait MessagePreparer {
  protected val US_ASCII = Charset.forName("US-ASCII")

  protected def appendHeader(name: String, value: String, sb: JStringBuilder) =
    appendLine(sb.append(name).append(':').append(' ').append(value))

  protected def appendContentLengthHeader(contentLength: Int, sb: JStringBuilder) =
    if (contentLength > 0) appendHeader("Content-Length", contentLength.toString, sb) else sb

  @tailrec
  protected final def appendHeaders(httpHeaders: List[HttpHeader], sb: JStringBuilder,
                                    connectionHeaderValue: Option[String] = None): Option[String] = {
    if (httpHeaders.isEmpty) {
      connectionHeaderValue
    } else {
      val header = httpHeaders.head
      val newConnectionHeaderValue = {
        if (connectionHeaderValue.isEmpty)
          if (header.name == "Connection") Some(header.value) else None
        else connectionHeaderValue
      }
      appendHeader(header.name, header.value, sb)
      appendHeaders(httpHeaders.tail, sb, newConnectionHeaderValue)
    }
  }

  protected def appendLine(sb: JStringBuilder) = sb.append('\r').append('\n')

  protected def wrapBody(body: Array[Byte]) = if (body.length == 0) Nil else ByteBuffer.wrap(body) :: Nil
}

trait ResponsePreparer extends MessagePreparer {
  protected def serverHeader: String

  private val ServerHeaderPlusDateColonSP =
    if (serverHeader.isEmpty) "Date: " else "Server: " + serverHeader + "\r\nDate: "

  protected def prepare(response: HttpResponse, reqProtocol: HttpProtocol,
                        reqConnectionHeader: Option[String]): (List[ByteBuffer], Boolean) = {
    import response._

    def appendStatusLine(sb: JStringBuilder) {
      if (status == 200)
        sb.append("HTTP/1.1 200 OK\r\n")
      else
        appendLine(sb.append("HTTP/1.1 ").append(status).append(' ').append(HttpResponse.defaultReason(status)))
    }

    def appendConnectionHeader(sb: JStringBuilder)(connectionHeaderValue: Option[String]) = {
      if (connectionHeaderValue.isEmpty) reqProtocol match {
        case `HTTP/1.0` =>
          if (reqConnectionHeader.isEmpty || reqConnectionHeader.get != "Keep-Alive") true
          else {
            appendHeader("Connection", "Keep-Alive", sb)
            false
          }
        case `HTTP/1.1` =>
          if (reqConnectionHeader.isDefined && reqConnectionHeader.get == "close") {
            appendHeader("Connection", "close", sb)
            true
          } else false
      } else {
        connectionHeaderValue.get.contains("close")
      }
    }

    val sb = new java.lang.StringBuilder(256)
    appendStatusLine(sb)
    val close = appendConnectionHeader(sb) {
      appendHeaders(headers, sb)
    }
    appendContentLengthHeader(body.length, sb)
    appendLine(sb.append(ServerHeaderPlusDateColonSP).append(dateTimeNow.toRfc1123DateTimeString))
    appendLine(sb)
    (ByteBuffer.wrap(sb.toString.getBytes(US_ASCII)) :: wrapBody(body), close)
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}

trait RequestPreparer extends MessagePreparer {
  protected def userAgentHeader: String

  protected def prepare(request: HttpRequest, host: String, port: Int): List[ByteBuffer] = {
    import request._

    def appendRequestLine(sb: JStringBuilder) {
      appendLine(sb.append(method.name).append(' ').append(uri).append(' ').append(protocol.name))
    }

    val sb = new java.lang.StringBuilder(256)
    appendRequestLine(sb)
    appendHeaders(headers, sb)
    appendHeader("Host", if (port == 80) host else host + ':' + port, sb)
    if (!userAgentHeader.isEmpty) appendHeader("User-Agent", userAgentHeader, sb)
    appendContentLengthHeader(body.length, sb)
    appendLine(sb)
    ByteBuffer.wrap(sb.toString.getBytes(US_ASCII)) :: wrapBody(body)
  }
}