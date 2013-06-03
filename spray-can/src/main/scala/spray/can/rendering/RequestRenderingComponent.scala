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
import spray.http._
import HttpHeaders._
import RenderSupport._

trait RequestRenderingComponent {
  def userAgent: Option[`User-Agent`]

  def renderRequestPart(r: Rendering, part: HttpRequestPart, serverAddress: InetSocketAddress): Unit = {
    def renderRequestStart(request: HttpRequest): Unit = {
      import request._
      @tailrec def renderHeaders(remaining: List[HttpHeader], hostHeaderSeen: Boolean = false): Unit =
        remaining match {
          case Nil ⇒ if (!hostHeaderSeen) r ~~ Host(serverAddress) ~~ CrLf
          case head :: tail ⇒
            val found = head.lowercaseName match {
              case "content-type" if !entity.isEmpty   ⇒ false // we never render these headers here,
              case "content-length"                    ⇒ false // because their production is the
              case "transfer-encoding"                 ⇒ false // responsibility of the spray-can layer,
              case "user-agent" if userAgent.isDefined ⇒ false // not the user
              case "host"                              ⇒ r ~~ head ~~ CrLf; true
              case _                                   ⇒ r ~~ head ~~ CrLf; false
            }
            renderHeaders(tail, found || hostHeaderSeen)
        }
      uri.renderWithoutFragment(r ~~ request.method ~~ ' ', UTF8) ~~ ' ' ~~ protocol ~~ CrLf
      renderHeaders(headers)
      if (userAgent.isDefined) r ~~ userAgent.get ~~ CrLf
      entity match {
        case HttpBody(contentType, _) ⇒ r ~~ `Content-Type` ~~ contentType ~~ CrLf
        case EmptyEntity              ⇒
      }
    }

    def renderRequest(request: HttpRequest): Unit = {
      renderRequestStart(request)
      val bodyLength = request.entity.buffer.length
      if (bodyLength > 0 || request.method.entityAccepted) r ~~ `Content-Length` ~~ bodyLength ~~ CrLf
      r ~~ CrLf ~~ request.entity.buffer
    }

    def renderChunkedRequestStart(request: HttpRequest): Unit = {
      renderRequestStart(request)
      r ~~ `Transfer-Encoding` ~~ Chunked ~~ CrLf ~~ CrLf
      if (request.entity.buffer.length > 0) r ~~ MessageChunk(request.entity.buffer)
    }

    part match {
      case x: HttpRequest         ⇒ renderRequest(x)
      case x: ChunkedRequestStart ⇒ renderChunkedRequestStart(x.request)
      case x: MessageChunk        ⇒ r ~~ x
      case x: ChunkedMessageEnd   ⇒ r ~~ x
    }
  }
}