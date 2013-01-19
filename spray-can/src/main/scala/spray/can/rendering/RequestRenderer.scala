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
import java.net.InetSocketAddress
import scala.annotation.tailrec
import spray.io.BufferBuilder
import spray.http._


class RequestRenderer(userAgentHeader: String, requestSizeHint: Int) extends MessageRendering {

  def render(requestPart: HttpRequestPart, remoteAddress: Option[InetSocketAddress]): RenderedMessagePart = {
    requestPart match {
      case x: HttpRequest => renderRequest(x, remoteAddress)
      case x: ChunkedRequestStart => renderChunkedRequestStart(x.request, remoteAddress)
      case x: MessageChunk => renderChunk(x, requestSizeHint)
      case x: ChunkedMessageEnd => renderFinalChunk(x, requestSizeHint)
    }
  }

  private def renderRequest(request: HttpRequest, remoteAddress: Option[InetSocketAddress]) = {
    val bb = renderRequestStart(request, remoteAddress)
    val rbl = request.entity.buffer.length
    if (rbl > 0 || request.method.entityAccepted) appendHeader("Content-Length", rbl.toString, bb)
    bb.append(MessageRendering.CrLf)
    RenderedMessagePart {
      if (rbl > 0)
        if (bb.remainingCapacity >= rbl) bb.append(request.entity.buffer).toByteBuffer :: Nil
        else bb.toByteBuffer :: ByteBuffer.wrap(request.entity.buffer) :: Nil
      else bb.toByteBuffer :: Nil
    }
  }

  private def renderChunkedRequestStart(request: HttpRequest, remoteAddress: Option[InetSocketAddress]) = {
    val bb = renderRequestStart(request, remoteAddress)
    appendHeader("Transfer-Encoding", "chunked", bb).append(MessageRendering.CrLf)
    val body = request.entity.buffer
    if (body.length > 0) renderChunk(Nil, body, bb)
    RenderedMessagePart(bb.toByteBuffer :: Nil)
  }

  private def renderRequestStart(request: HttpRequest, remoteAddress: Option[InetSocketAddress]) = {
    import request._
    val bb = BufferBuilder(requestSizeHint)
    bb.append(method.value).append(' ').append(uri).append(' ').append(protocol.value).append(MessageRendering.CrLf)
    val hostHeaderPresent = appendHeaders(headers, bb)
    if (!hostHeaderPresent && remoteAddress.isDefined) {
      bb.append("Host: ").append(remoteAddress.get.getHostName)
      val port = remoteAddress.get.getPort
      if (port != 80) bb.append(':').append(Integer.toString(port))
      bb.append(MessageRendering.CrLf)
    }
    if (!userAgentHeader.isEmpty) appendHeader("User-Agent", userAgentHeader, bb)
    appendContentTypeHeaderIfRequired(request.entity, bb)
  }

  @tailrec
  private def appendHeaders(httpHeaders: List[HttpHeader], bb: BufferBuilder,
                            hostHeaderPresent: Boolean = false): Boolean = {
    if (httpHeaders.nonEmpty) {
      val header = httpHeaders.head
      var newHostHeaderPresent = hostHeaderPresent
      header.lowercaseName match {
        case "content-type"      => // we never render these headers here,
        case "content-length"    => // because their production is the
        case "transfer-encoding" => // responsibility of the spray-can layer,
        case "user-agent" if !userAgentHeader.isEmpty
                                 => // not the user
        case "host"              => newHostHeaderPresent = true; appendHeader(header, bb)
        case _                   => appendHeader(header, bb)
      }
      appendHeaders(httpHeaders.tail, bb, newHostHeaderPresent)
    } else hostHeaderPresent
  }
}
