/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import akka.event.LoggingAdapter
import spray.util.UTF8
import spray.http._
import HttpHeaders._
import RenderSupport._

trait RequestRenderingComponent {
  def userAgent: Option[`User-Agent`]

  def renderRequestPart(r: Rendering, part: HttpRequestPart, serverAddress: InetSocketAddress,
                        log: LoggingAdapter): Unit = {
    def renderRequestStart(request: HttpRequest): Unit = {
      import request._
      @tailrec def renderHeaders(remaining: List[HttpHeader], hostHeaderSeen: Boolean = false): Unit =
        remaining match {
          case Nil ⇒ if (!hostHeaderSeen) r ~~ Host(serverAddress) ~~ CrLf
          case head :: tail ⇒
            def logHeaderSuppressionWarning(msg: String): Boolean = {
              log.warning("Explicitly set request header '{}' is ignored, {}", head, msg)
              false
            }
            val found = head.lowercaseName match {
              case "content-type" if entity.nonEmpty ⇒
                logHeaderSuppressionWarning("the request Content-Type is set via the request's HttpEntity!")
              case "content-length" | "transfer-encoding" ⇒
                logHeaderSuppressionWarning("the spray-can HTTP layer sets this header automatically!")
              case "user-agent" if userAgent.isDefined ⇒
                logHeaderSuppressionWarning("the configured User-Agent header overrides the given one!")
              case "host" ⇒ r ~~ head ~~ CrLf; true
              case _      ⇒ r ~~ head ~~ CrLf; false
            }
            renderHeaders(tail, found || hostHeaderSeen)
        }
      uri.renderWithoutFragment(r ~~ request.method ~~ ' ', UTF8) ~~ ' ' ~~ protocol ~~ CrLf
      renderHeaders(headers)
      if (userAgent.isDefined) r ~~ userAgent.get ~~ CrLf
      entity match {
        case HttpEntity.NonEmpty(ContentTypes.NoContentType, _) | HttpEntity.Empty ⇒ // don't render Content-Type header
        case HttpEntity.NonEmpty(contentType, _)                                   ⇒ r ~~ `Content-Type` ~~ contentType ~~ CrLf
      }
    }

    def renderRequest(request: HttpRequest): Unit = {
      renderRequestStart(request)
      val bodyLength = request.entity.data.length
      if (bodyLength > 0 || request.method.entityAccepted) r ~~ `Content-Length` ~~ bodyLength ~~ CrLf
      r ~~ CrLf ~~ request.entity.data
    }

    def renderChunkedRequestStart(request: HttpRequest): Unit = {
      renderRequestStart(request)
      r ~~ `Transfer-Encoding` ~~ Chunked ~~ CrLf ~~ CrLf
      request.entity match {
        case x: HttpEntity.NonEmpty ⇒ r ~~ MessageChunk(x.data)
        case _                      ⇒ // nothing to do
      }
    }

    part match {
      case x: HttpRequest         ⇒ renderRequest(x)
      case x: ChunkedRequestStart ⇒ renderChunkedRequestStart(x.request)
      case x: MessageChunk        ⇒ r ~~ x
      case x: ChunkedMessageEnd   ⇒ r ~~ x
    }
  }
}