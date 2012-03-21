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
import cc.spray.util.DateTime

class ResponseRenderer(serverHeader: String, chunklessStreaming: Boolean) extends MessageRendering {

  private[this] val serverHeaderPlusDateColonSP = serverHeader match {
    case "" => "Date: "
    case x => "Server: " + x + "\r\nDate: "
  }

  def render(ctx: HttpResponsePartRenderingContext): RenderedMessagePart = {
    def chunkless = chunklessStreaming || ctx.requestProtocol == `HTTP/1.0`
    ctx.responsePart match {
      case x: HttpResponse => renderResponse(x, ctx)
      case x: ChunkedResponseStart => renderChunkedResponseStart(x.response, ctx, chunkless)
      case x: MessageChunk => renderChunk(x, chunkless)
      case x: ChunkedMessageEnd => renderFinalChunk(x, ctx.requestConnectionHeader, chunkless)
    }
  }

  private def renderResponse(response: HttpResponse, ctx: HttpResponsePartRenderingContext) = {
    import response._

    val sb = new JStringBuilder(256)
    val connectionHeaderValue = renderResponseStart(response, ctx, sb)
    val close = appendConnectionHeaderIfRequired(response, ctx, connectionHeaderValue, sb)
    appendServerAndDateHeader(sb)

    // don't set a Content-Length header for non-keepalive HTTP/1.0 responses (rely on body end by connection close)
    if (response.protocol == `HTTP/1.1` || !close) appendHeader("Content-Length", body.length.toString, sb)
    appendLine(sb)
    val bodyBufs = if (body.length == 0 || ctx.requestMethod == HttpMethods.HEAD) Nil else ByteBuffer.wrap(body) :: Nil
    RenderedMessagePart(encode(sb) :: bodyBufs, close)
  }

  private def renderChunkedResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                         chunkless: Boolean) = {
    import response._

    val sb = new JStringBuilder(256)
    renderResponseStart(response, ctx, sb)
    if (!chunkless) appendHeader("Transfer-Encoding", "chunked", sb)
    appendServerAndDateHeader(sb)
    appendLine(sb)
    val bodyBufs =
      if (body.length == 0 || ctx.requestMethod == HttpMethods.HEAD) Nil
      else renderChunk(Nil, body, chunkless)
    RenderedMessagePart(encode(sb) :: bodyBufs, closeConnection = false)
  }

  private def renderResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                  sb: JStringBuilder): Option[String] = {
    import response._

    if (status == 200 && protocol == `HTTP/1.1`) {
      sb.append("HTTP/1.1 200 OK\r\n")
    } else appendLine {
      sb.append(protocol.name).append(' ').append(status).append(' ').append(HttpResponse.defaultReason(status))
    }
    appendHeaders(headers, sb)
  }

  def appendConnectionHeaderIfRequired(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                       connectionHeaderValue: Option[String], sb: JStringBuilder): Boolean = {
    ctx.requestProtocol match {
      case `HTTP/1.0` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && ctx.requestConnectionHeader.get == "Keep-Alive") {
            appendHeader("Connection", "Keep-Alive", sb)
            false
          } else true
        } else !connectionHeaderValue.get.contains("Keep-Alive")
      }
      case `HTTP/1.1` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && ctx.requestConnectionHeader.get == "close") {
            if (response.protocol == `HTTP/1.1`) appendHeader("Connection", "close", sb)
            true
          } else response.protocol == `HTTP/1.0`
        } else connectionHeaderValue.get.contains("close")
      }
    }
  }

  def appendServerAndDateHeader(sb: JStringBuilder) {
    if (!serverHeaderPlusDateColonSP.isEmpty)
      appendLine(sb.append(serverHeaderPlusDateColonSP).append(dateTimeNow.toRfc1123DateTimeString))
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}