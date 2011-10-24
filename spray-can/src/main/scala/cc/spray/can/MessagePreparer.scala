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
import HttpProtocols._
import java.lang.{StringBuilder => JStringBuilder}

private[can] trait MessagePreparer {
  private val CrLf = "\r\n".getBytes("ASCII")

  protected def appendHeader(name: String, value: String, sb: JStringBuilder) =
    appendLine(sb.append(name).append(':').append(' ').append(value))

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

  protected def encode(sb: JStringBuilder): ByteBuffer = {
    val chars = new Array[Char](sb.length)
    sb.getChars(0, sb.length, chars, 0)
    val buf = ByteBuffer.allocate(sb.length)
    var i = 0
    while (i < chars.length) {
      buf.put(chars(i).asInstanceOf[Byte])
      i += 1
    }
    buf.flip()
    buf
  }

  def prepareChunk(extensions: List[ChunkExtension], body: Array[Byte]) = {
    val sb = new java.lang.StringBuilder(16)
    sb.append(body.length.toHexString)
    appendLine(appendChunkExtensions(extensions, sb))
    encode(sb) :: ByteBuffer.wrap(body) :: ByteBuffer.wrap(CrLf) :: Nil
  }

  def prepareFinalChunk(extensions: List[ChunkExtension], trailer: List[HttpHeader]) = {
    val sb = new java.lang.StringBuilder(16)
    appendLine(appendChunkExtensions(extensions, sb.append("0")))
    appendHeaders(trailer, sb)
    appendLine(sb)
    encode(sb) :: Nil
  }

  @tailrec
  protected final def appendChunkExtensions(extensions: List[ChunkExtension], sb: JStringBuilder): JStringBuilder = {
    extensions match {
      case Nil => sb
      case ChunkExtension(name, value) :: rest => appendChunkExtensions(rest, {
        sb.append(';').append(name).append('=')
        if (value.forall(isTokenChar)) sb.append(value) else sb.append('"').append(value).append('"')
      })
    }
  }
}

private[can] trait ResponsePreparer extends MessagePreparer {
  protected def serverHeader: String

  private val ServerHeaderPlusDateColonSP =
    if (serverHeader.isEmpty) "Date: " else "Server: " + serverHeader + "\r\nDate: "

  protected def prepareResponse(requestLine: RequestLine, response: HttpResponse,
                                reqConnectionHeader: Option[String]): (List[ByteBuffer], Boolean) = {
    import response._
    val (sb, close) = prepareResponseStart(requestLine, response, reqConnectionHeader)
    // don't set a Content-Length header for non-keepalive HTTP/1.0 responses (rely on body end by connection close)
    if (response.protocol == `HTTP/1.1` || !close) appendHeader("Content-Length", body.length.toString, sb)
    appendLine(sb)
    val bodyBufs = if (body.length == 0 || requestLine.method == HttpMethods.HEAD) Nil else ByteBuffer.wrap(body) :: Nil
    (encode(sb) :: bodyBufs, close)
  }

  protected def prepareChunkedResponseStart(requestLine: RequestLine, response: HttpResponse,
                                            reqConnectionHeader: Option[String]): (List[ByteBuffer], Boolean) = {
    import response._
    val (sb, close) = prepareResponseStart(requestLine, response, reqConnectionHeader)
    appendHeader("Transfer-Encoding", "chunked", sb)
    appendLine(sb)
    val bodyBufs = if (body.length == 0 || requestLine.method == HttpMethods.HEAD) Nil else prepareChunk(Nil, body)
    (encode(sb) :: bodyBufs, close)
  }

  private def prepareResponseStart(requestLine: RequestLine, response: HttpResponse,
                                   reqConnectionHeader: Option[String]) = {
    import response._

    def appendConnectionHeaderIfRequired(connectionHeaderValue: Option[String], sb: JStringBuilder) = {
      requestLine.protocol match {
        case `HTTP/1.0` => {
          if (connectionHeaderValue.isEmpty) {
            if (reqConnectionHeader.isDefined && reqConnectionHeader.get == "Keep-Alive") {
              appendHeader("Connection", "Keep-Alive", sb)
              false
            } else true
          } else !connectionHeaderValue.get.contains("Keep-Alive")
        }
        case `HTTP/1.1` => {
          if (connectionHeaderValue.isEmpty) {
            if (reqConnectionHeader.isDefined && reqConnectionHeader.get == "close") {
              if (response.protocol == `HTTP/1.1`) appendHeader("Connection", "close", sb)
              true
            } else response.protocol == `HTTP/1.0`
          } else connectionHeaderValue.get.contains("close")
        }
      }
    }

    val sb = new java.lang.StringBuilder(256)
    if (status == 200 && protocol == `HTTP/1.1`) {
      sb.append("HTTP/1.1 200 OK\r\n")
    } else appendLine {
      sb.append(protocol.name).append(' ').append(status).append(' ').append(HttpResponse.defaultReason(status))
    }
    val connectionHeaderValue = appendHeaders(headers, sb)
    val close = appendConnectionHeaderIfRequired(connectionHeaderValue, sb)
    appendLine(sb.append(ServerHeaderPlusDateColonSP).append(dateTimeNow.toRfc1123DateTimeString))
    (sb, close)
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}

private[can] trait RequestPreparer extends MessagePreparer {
  protected def userAgentHeader: String

  protected def prepareRequest(request: HttpRequest, host: String, port: Int): List[ByteBuffer] = {
    val sb = prepareRequestStart(request, host, port)
    val bodyBufs = if (request.body.length > 0) {
      appendHeader("Content-Length", request.body.length.toString, sb)
      appendLine(sb)
      ByteBuffer.wrap(request.body) :: Nil
    } else {
      appendLine(sb)
      Nil
    }
    encode(sb) :: bodyBufs
  }

  protected def prepareChunkedRequestStart(request: HttpRequest, host: String, port: Int): List[ByteBuffer] = {
    val sb = prepareRequestStart(request, host, port)
    appendHeader("Transfer-Encoding", "chunked", sb)
    appendLine(sb)
    encode(sb) :: { if (request.body.length > 0) prepareChunk(Nil, request.body) else Nil }
  }

  private def prepareRequestStart(request: HttpRequest, host: String, port: Int) = {
    import request._
    val sb = new java.lang.StringBuilder(256)
    appendLine(sb.append(method.name).append(' ').append(uri).append(' ').append(protocol.name))
    appendHeaders(headers, sb)
    appendHeader("Host", if (port == 80) host else host + ':' + port, sb)
    if (!userAgentHeader.isEmpty) appendHeader("User-Agent", userAgentHeader, sb)
    sb
  }
}