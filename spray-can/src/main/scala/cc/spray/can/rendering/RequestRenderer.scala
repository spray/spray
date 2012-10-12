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

package spray.can.rendering

import java.nio.ByteBuffer
import spray.io.BufferBuilder
import spray.http._


class RequestRenderer(userAgentHeader: String, requestSizeHint: Int) extends MessageRendering {

  def render(ctx: HttpRequestPartRenderingContext): RenderedMessagePart = {
    ctx.requestPart match {
      case x: HttpRequest => renderRequest(x, ctx.host, ctx.port)
      case x: ChunkedRequestStart => renderChunkedRequestStart(x.request, ctx.host, ctx.port)
      case x: MessageChunk => renderChunk(x, requestSizeHint)
      case x: ChunkedMessageEnd => renderFinalChunk(x, requestSizeHint)
    }
  }

  private def renderRequest(request: HttpRequest, host: String, port: Int) = {
    val bb = renderRequestStart(request, host, port)
    val rbl = request.entity.buffer.length
    RenderedMessagePart {
      if (rbl > 0) {
        appendHeader("Content-Length", rbl.toString, bb).append(MessageRendering.CrLf)
        if (bb.remainingCapacity >= rbl) bb.append(request.entity.buffer).toByteBuffer :: Nil
        else bb.toByteBuffer :: ByteBuffer.wrap(request.entity.buffer) :: Nil
      } else bb.append(MessageRendering.CrLf).toByteBuffer :: Nil
    }
  }

  private def renderChunkedRequestStart(request: HttpRequest, host: String, port: Int) = {
    val bb = renderRequestStart(request, host, port)
    appendHeader("Transfer-Encoding", "chunked", bb).append(MessageRendering.CrLf)
    val body = request.entity.buffer
    if (body.length > 0) renderChunk(Nil, body, bb)
    RenderedMessagePart(bb.toByteBuffer :: Nil)
  }

  private def renderRequestStart(request: HttpRequest, host: String, port: Int) = {
    import request.{host => _, port => _, _}
    val bb = BufferBuilder(requestSizeHint)
    bb.append(method.value).append(' ').append(uri).append(' ').append(protocol.value).append(MessageRendering.CrLf)
    appendHeaders(headers, bb)
    bb.append("Host: ").append(host)
    if (port != 80) bb.append(':').append(Integer.toString(port))
    bb.append(MessageRendering.CrLf)
    if (!userAgentHeader.isEmpty) appendHeader("User-Agent", userAgentHeader, bb)
    appendContentTypeHeaderIfRequired(request.entity, bb)
  }
}