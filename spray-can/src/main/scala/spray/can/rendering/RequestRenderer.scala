/*
 * Copyright (C) 2011-2013 spray.io
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

import java.net.InetSocketAddress
import scala.annotation.tailrec
import akka.util.ByteStringBuilder
import spray.http._
import MessageRendering._
import HttpHeaders._

class RequestRenderer(userAgentHeader: String, requestSizeHint: Int) {

  def render(requestPart: HttpRequestPart, remoteAddress: InetSocketAddress): RenderedMessagePart = {
    requestPart match {
      case x: HttpRequest         ⇒ renderRequest(x, remoteAddress)
      case x: ChunkedRequestStart ⇒ renderChunkedRequestStart(x.request, remoteAddress)
      case x: MessageChunk        ⇒ renderChunk(x, requestSizeHint)
      case x: ChunkedMessageEnd   ⇒ renderFinalChunk(x, requestSizeHint)
    }
  }

  private def renderRequest(request: HttpRequest, remoteAddress: InetSocketAddress) = {
    implicit val bb = renderRequestStart(request, remoteAddress)
    val bodyLength = request.entity.buffer.length
    if (bodyLength > 0 || request.method.entityAccepted) putHeader(ContentLength, bodyLength.toString)
    put(CrLf).put(request.entity.buffer)
    RenderedMessagePart(bb.result())
  }

  private def renderChunkedRequestStart(request: HttpRequest, remoteAddress: InetSocketAddress): RenderedMessagePart = {
    implicit val bb = renderRequestStart(request, remoteAddress)
    putHeaderBytes(TransferEncoding, Chunked).put(CrLf)
    if (request.entity.buffer.length > 0) putChunk(request.entity.buffer)
    RenderedMessagePart(bb.result())
  }

  private def renderRequestStart(request: HttpRequest, remoteAddress: InetSocketAddress): ByteStringBuilder = {
    import request._
    implicit val bb = newByteStringBuilder(requestSizeHint)
    // TODO: extend Uri to directly render into byte array
    val uriWithoutFragment = if (uri.fragment.isEmpty) uri else uri.copy(fragment = None)
    put(method.value).put(' ').put(uriWithoutFragment.toString).put(' ').put(protocol.value).put(CrLf)
    val hostHeaderPresent = putHeadersAndReturnHostHeaderPresent(headers, !request.entity.isEmpty)
    if (!hostHeaderPresent) {
      put("Host: ").put(remoteAddress.getHostName)
      val port = remoteAddress.getPort
      if (port != 0) put(':').put(Integer.toString(port))
      put(CrLf)
    }
    if (!userAgentHeader.isEmpty) putHeader(UserAgent, userAgentHeader)
    putContentTypeHeaderIfRequired(request.entity)
    bb
  }

  @tailrec
  private def putHeadersAndReturnHostHeaderPresent(headers: List[HttpHeader], entityPresent: Boolean, hostHeaderPresent: Boolean = false)(implicit bb: ByteStringBuilder): Boolean =
    headers match {
      case Nil ⇒ hostHeaderPresent
      case head :: tail ⇒
        val found = head.lowercaseName match {
          case "content-type" if entityPresent          ⇒ false // we never render these headers here,
          case "content-length"                         ⇒ false // because their production is the
          case "transfer-encoding"                      ⇒ false // responsibility of the spray-can layer,
          case "user-agent" if !userAgentHeader.isEmpty ⇒ false // not the user
          case "host"                                   ⇒ put(head); true
          case _                                        ⇒ put(head); false
        }
        putHeadersAndReturnHostHeaderPresent(tail, entityPresent, found || hostHeaderPresent)
    }
}
