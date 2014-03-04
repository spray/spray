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

private[can] trait RequestRenderingComponent {
  def userAgent: Option[`User-Agent`]
  def chunklessStreaming: Boolean

  def renderRequestPartRenderingContext(r: Rendering, ctx: RequestPartRenderingContext, serverAddress: InetSocketAddress,
                                        log: LoggingAdapter): Unit = {
    def renderRequestStart(request: HttpRequest, allowUserContentType: Boolean,
                           userSpecifiedContentLength: Boolean): Unit = {
      def render(h: HttpHeader) = r ~~ h ~~ CrLf
      def suppressionWarning(h: HttpHeader, msg: String = "the spray-can HTTP layer sets this header automatically!"): Unit =
        log.warning("Explicitly set request header '{}' is ignored, {}", h, msg)

      @tailrec def renderHeaders(remaining: List[HttpHeader], hostHeaderSeen: Boolean = false,
                                 userAgentSeen: Boolean = false, contentTypeSeen: Boolean = false,
                                 contentLengthSeen: Boolean = false): Unit =
        remaining match {
          case head :: tail ⇒ head match {
            case x: `Content-Type` ⇒
              val seen =
                if (contentTypeSeen) { suppressionWarning(x, "another `Content-Type` header was already rendered"); true }
                else if (!allowUserContentType) { suppressionWarning(x, "the request Content-Type is set via the request's HttpEntity!"); false }
                else { render(x); true }
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen = seen, contentLengthSeen)

            case x: `Content-Length` ⇒
              val seen =
                if (contentLengthSeen) { suppressionWarning(x, "another `Content-Length` header was already rendered"); true }
                else if (!userSpecifiedContentLength) { suppressionWarning(x); false }
                else { render(x); true }
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen, contentLengthSeen = seen)

            case `Transfer-Encoding`(_) ⇒
              suppressionWarning(head)
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen, contentLengthSeen)

            case x: `Host` ⇒
              render(x)
              renderHeaders(tail, hostHeaderSeen = true, userAgentSeen, contentTypeSeen, contentLengthSeen)

            case x: `User-Agent` ⇒
              render(x)
              renderHeaders(tail, hostHeaderSeen, userAgentSeen = true, contentTypeSeen, contentLengthSeen)

            case x: `Raw-Request-URI` ⇒
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen, contentLengthSeen)

            case x: RawHeader if x.lowercaseName == "content-type" ||
              x.lowercaseName == "content-length" ||
              x.lowercaseName == "transfer-encoding" ||
              x.lowercaseName == "host" ||
              x.lowercaseName == "user-agent" ⇒
              suppressionWarning(x, "illegal RawHeader")
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen, contentLengthSeen)

            case x ⇒
              render(x)
              renderHeaders(tail, hostHeaderSeen, userAgentSeen, contentTypeSeen, contentLengthSeen)
          }

          case Nil ⇒
            if (!hostHeaderSeen) r ~~ Host(serverAddress) ~~ CrLf
            if (!userAgentSeen && userAgent.isDefined) r ~~ userAgent.get ~~ CrLf
            if (!contentLengthSeen && userSpecifiedContentLength)
              throw new RuntimeException("Chunkless streamed request is missing user-specified Content-Length header")
            request.entity match {
              case HttpEntity.NonEmpty(ContentTypes.NoContentType, _) ⇒
              case HttpEntity.NonEmpty(contentType, _) if !contentTypeSeen ⇒ r ~~ `Content-Type` ~~ contentType ~~ CrLf
              case _ ⇒
            }
        }

      def renderRequestLine(): Unit = {
        @tailrec def renderUri(headers: List[HttpHeader]): Unit = {
          headers match {
            case head :: tail ⇒ head match {
              case x: `Raw-Request-URI` ⇒ x.renderValue(r)
              case _                    ⇒ renderUri(tail)
            }
            case Nil ⇒ request.uri.renderWithoutFragment(r, UTF8)
          }
        }

        r ~~ request.method ~~ ' '
        renderUri(request.headers)
        r ~~ ' ' ~~ request.protocol ~~ CrLf
      }

      renderRequestLine()
      renderHeaders(request.headers)
    }

    def chunkless = chunklessStreaming || (ctx.requestProtocol eq HttpProtocols.`HTTP/1.0`)

    def renderRequest(request: HttpRequest): Unit = {
      renderRequestStart(request, allowUserContentType = false, userSpecifiedContentLength = false)
      val bodyLength = request.entity.data.length
      if (bodyLength > 0 || request.method.isEntityAccepted) r ~~ `Content-Length` ~~ bodyLength ~~ CrLf
      r ~~ CrLf ~~ request.entity.data
    }

    def renderChunkedRequestStart(request: HttpRequest): Unit = {
      renderRequestStart(request, allowUserContentType = request.entity.isEmpty, userSpecifiedContentLength = chunkless)
      if (!chunkless) r ~~ `Transfer-Encoding` ~~ Chunked ~~ CrLf
      r ~~ CrLf
      request.entity match {
        case x: HttpEntity.NonEmpty ⇒ if (chunkless) r ~~ x.data else r ~~ MessageChunk(x.data)
        case _                      ⇒ // nothing to do
      }
    }

    ctx.requestPart match {
      case x: HttpRequest         ⇒ renderRequest(x)
      case x: ChunkedRequestStart ⇒ renderChunkedRequestStart(x.request)
      case x: MessageChunk        ⇒ if (chunkless) r ~~ x.data else r ~~ x
      case x: ChunkedMessageEnd   ⇒ if (!chunkless) r ~~ x
    }
  }
}
