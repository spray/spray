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

package spray.can
package rendering

import scala.annotation.tailrec
import akka.util.{ByteStringBuilder, ByteString}
import spray.util._
import spray.http._
import HttpProtocols._
import MessageRendering._

class ResponseRenderer(serverHeader: String,
                       chunklessStreaming: Boolean,
                       responseSizeHint: Int) {

  private[this] val serverHeaderPlusDateColonSP = (serverHeader match {
    case "" => "Date: "
    case x => "Server: " + x + "\r\nDate: "
  }).getAsciiBytes

  def render(ctx: HttpResponsePartRenderingContext): RenderedMessagePart = {
    def chunkless = chunklessStreaming || ctx.requestProtocol == `HTTP/1.0`
    ctx.responsePart match {
      case x: HttpResponse => renderResponse(x, ctx)

      case x: ChunkedResponseStart => renderChunkedResponseStart(x.response, ctx, chunkless)

      case x: MessageChunk if ctx.requestMethod != HttpMethods.HEAD =>
        if (chunkless) RenderedMessagePart(ByteString(x.body))
        else renderChunk(x, responseSizeHint)

      case x: ChunkedMessageEnd if ctx.requestMethod != HttpMethods.HEAD =>
        if (chunkless) RenderedMessagePart(ByteString.empty, closeConnection = true)
        else renderFinalChunk(x, responseSizeHint, ctx.requestConnectionHeader)

      case _ => RenderedMessagePart(ByteString.empty)
    }
  }

  private def renderResponse(response: HttpResponse, ctx: HttpResponsePartRenderingContext) = {
    import response._

    implicit val bb = newByteStringBuilder(responseSizeHint)
    val connectionHeaderValue = renderResponseStart(response, ctx)
    val close = putConnectionHeaderIfRequiredAndReturnClose(response, ctx, connectionHeaderValue)

    // don't set a Content-Length header for non-keep-alive HTTP/1.0 responses (rely on body end by connection close)
    if (response.protocol == `HTTP/1.1` || !close) putHeader("Content-Length", Integer.toString(entity.buffer.length))
    put(CrLf)

    if (entity.buffer.length > 0 && ctx.requestMethod != HttpMethods.HEAD) put(entity.buffer)
    RenderedMessagePart(bb.result(), close)
  }

  private def renderChunkedResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                         chunkless: Boolean) = {
    import response._

    implicit val bb = newByteStringBuilder(responseSizeHint)
    renderResponseStart(response, ctx)
    if (!chunkless) putHeader("Transfer-Encoding", "chunked")
    put(CrLf)

    if (ctx.requestMethod != HttpMethods.HEAD && entity.buffer.length > 0) {
      if (chunkless) put(entity.buffer)
      else putChunk(Nil, entity.buffer)
    }
    RenderedMessagePart(bb.result())
  }

  private def renderResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext)
                                 (implicit bb: ByteStringBuilder): Option[String] = {
    import response._

    if (status == StatusCodes.OK && protocol == `HTTP/1.1`) put(DefaultStatusLine)
    else put(protocol.value).put(' ').put(Integer.toString(status.value)).put(' ').put(status.reason).put(CrLf)
    put(serverAndDateHeader)
    putContentTypeHeaderIfRequired(entity)
    putHeadersAndReturnConnectionHeaderValue(headers)()
  }

  @tailrec
  private def putHeadersAndReturnConnectionHeaderValue(headers: List[HttpHeader])
                                                      (connectionHeaderValue: Option[String] = None)
                                                      (implicit bb: ByteStringBuilder): Option[String] =
    headers match {
      case Nil => connectionHeaderValue
      case head :: tail => putHeadersAndReturnConnectionHeaderValue(tail) {
        head.lowercaseName match {
          case "content-type"      => None // we never render these headers here,
          case "content-length"    => None // because their production is the
          case "transfer-encoding" => None // responsibility of the spray-can layer,
          case "date"              => None // not the user
          case "server"            => None
          case "connection"        => put(head); Some(head.value)
          case _ => put(head); None
        }
      }
    }

  private def putConnectionHeaderIfRequiredAndReturnClose(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                                          connectionHeaderValue: Option[String])
                                                         (implicit bb: ByteStringBuilder): Boolean =
    ctx.requestProtocol match {
      case `HTTP/1.0` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && (ctx.requestConnectionHeader.get equalsIgnoreCase "Keep-Alive")) {
            putHeader("Connection", "Keep-Alive")
            false
          } else true
        } else !(connectionHeaderValue.get equalsIgnoreCase "Keep-Alive")
      }
      case `HTTP/1.1` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && (ctx.requestConnectionHeader.get equalsIgnoreCase "close")) {
            if (response.protocol == `HTTP/1.1`) putHeader("Connection", "close")
            true
          } else response.protocol == `HTTP/1.0`
        } else connectionHeaderValue.get equalsIgnoreCase "close"
      }
    }

  // for max perf we cache the ServerAndDateHeader of the last second here
  @volatile private[this] var cachedServerAndDateHeader: (Long, Array[Byte]) = _

  private def serverAndDateHeader: Array[Byte] = {
    var (cachedSeconds, cachedBytes) = if (cachedServerAndDateHeader != null) cachedServerAndDateHeader else (0L, null)
    val now = System.currentTimeMillis
    if (now / 1000 != cachedSeconds) {
      cachedSeconds = now / 1000
      implicit val bb = newByteStringBuilder(serverHeaderPlusDateColonSP.length + 32)
      put(serverHeaderPlusDateColonSP).put(dateTime(now).toRfc1123DateTimeString).put(CrLf)
      cachedBytes = bb.result().toArray
      cachedServerAndDateHeader = cachedSeconds -> cachedBytes
    }
    cachedBytes
  }

  protected def dateTime(now: Long) = DateTime(now)  // split out so we can stabilize by overriding in tests
}