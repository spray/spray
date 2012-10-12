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

import java.nio.ByteBuffer
import spray.io.BufferBuilder
import spray.util._
import spray.http._
import HttpProtocols._


class ResponseRenderer(serverHeader: String,
                       chunklessStreaming: Boolean,
                       responseSizeHint: Int) extends MessageRendering {

  private[this] val serverHeaderPlusDateColonSP = (serverHeader match {
    case "" => "Date: "
    case x => "Server: " + x + "\r\nDate: "
  }).getAsciiBytes

  def render(ctx: HttpResponsePartRenderingContext): RenderedMessagePart = {
    def chunkless = chunklessStreaming || ctx.requestProtocol == `HTTP/1.0`
    ctx.responsePart match {
      case x: HttpResponse => renderResponse(x, ctx)
      case x: ChunkedResponseStart => renderChunkedResponseStart(x.response, ctx, chunkless)
      case x: MessageChunk =>
        if (chunkless) RenderedMessagePart(ByteBuffer.wrap(x.body) :: Nil)
        else renderChunk(x, responseSizeHint)
      case x: ChunkedMessageEnd =>
        if (chunkless) RenderedMessagePart(Nil, closeConnection = true)
        else renderFinalChunk(x, responseSizeHint, ctx.requestConnectionHeader)
    }
  }

  private def renderResponse(response: HttpResponse, ctx: HttpResponsePartRenderingContext) = {
    import response._

    val bb = BufferBuilder(responseSizeHint)
    val connectionHeaderValue = renderResponseStart(response, ctx, bb)
    val close = appendConnectionHeaderIfRequired(response, ctx, connectionHeaderValue, bb)
    appendServerAndDateHeader(bb)
    appendContentTypeHeaderIfRequired(entity, bb)

    // don't set a Content-Length header for non-keepalive HTTP/1.0 responses (rely on body end by connection close)
    if (response.protocol == `HTTP/1.1` || !close) appendHeader("Content-Length", entity.buffer.length.toString, bb)

    bb.append(MessageRendering.CrLf)
    renderedMessagePart(bb, ctx.requestMethod, entity, close)
  }

  private def renderChunkedResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                         chunkless: Boolean) = {
    import response._

    val bb = BufferBuilder(responseSizeHint)
    renderResponseStart(response, ctx, bb)
    if (!chunkless) appendHeader("Transfer-Encoding", "chunked", bb)
    appendServerAndDateHeader(bb)
    appendContentTypeHeaderIfRequired(entity, bb)

    bb.append(MessageRendering.CrLf)
    if (chunkless || entity.buffer.length == 0 || ctx.requestMethod == HttpMethods.HEAD) {
      renderedMessagePart(bb, ctx.requestMethod, entity, close = false)
    } else {
      RenderedMessagePart(renderChunk(Nil, entity.buffer, bb).toByteBuffer :: Nil)
    }
  }

  private def renderResponseStart(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                  bb: BufferBuilder): Option[String] = {
    import response._

    if (status == 200 && protocol == `HTTP/1.1`) {
      bb.append(MessageRendering.DefaultStatusLine)
    } else {
      bb.append(protocol.value).append(' ').append(Integer.toString(status.value)).append(' ')
        .append(status.reason).append(MessageRendering.CrLf)
    }
    appendHeaders(headers, bb)
  }

  private def renderedMessagePart(bb: BufferBuilder, requestMethod: HttpMethod, entity: HttpEntity, close: Boolean) = {
    if (entity.buffer.length == 0 || requestMethod == HttpMethods.HEAD)
      RenderedMessagePart(bb.toByteBuffer :: Nil, close)
    else if (bb.remainingCapacity >= entity.buffer.length)
      RenderedMessagePart(bb.append(entity.buffer).toByteBuffer :: Nil, close)
    else
      RenderedMessagePart(bb.toByteBuffer :: ByteBuffer.wrap(entity.buffer) :: Nil, close)
  }

  private def appendConnectionHeaderIfRequired(response: HttpResponse, ctx: HttpResponsePartRenderingContext,
                                       connectionHeaderValue: Option[String], bb: BufferBuilder): Boolean = {
    ctx.requestProtocol match {
      case `HTTP/1.0` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && ctx.requestConnectionHeader.get == "Keep-Alive") {
            appendHeader("Connection", "Keep-Alive", bb)
            false
          } else true
        } else !connectionHeaderValue.get.contains("Keep-Alive")
      }
      case `HTTP/1.1` => {
        if (connectionHeaderValue.isEmpty) {
          if (ctx.requestConnectionHeader.isDefined && ctx.requestConnectionHeader.get == "close") {
            if (response.protocol == `HTTP/1.1`) appendHeader("Connection", "close", bb)
            true
          } else response.protocol == `HTTP/1.0`
        } else connectionHeaderValue.get.contains("close")
      }
    }
  }

  def appendServerAndDateHeader(bb: BufferBuilder) {
    if (!serverHeaderPlusDateColonSP.isEmpty)
      bb.append(serverHeaderPlusDateColonSP).append(dateTimeNow.toRfc1123DateTimeString).append(MessageRendering.CrLf)
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}