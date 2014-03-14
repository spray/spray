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

package spray.can
package rendering

import scala.annotation.tailrec
import akka.event.LoggingAdapter
import spray.util._
import spray.http._
import HttpProtocols._
import HttpHeaders._
import RenderSupport._

private[can] trait ResponseRenderingComponent {
  def serverHeaderValue: String
  def chunklessStreaming: Boolean

  private[this] val serverHeaderPlusDateColonSP =
    serverHeaderValue match {
      case "" ⇒ "Date: ".getAsciiBytes
      case x  ⇒ ("Server: " + x + "\r\nDate: ").getAsciiBytes
    }

  // returns a boolean indicating whether the connection is to be closed after this response was sent
  def renderResponsePartRenderingContext(r: Rendering, ctx: ResponsePartRenderingContext,
                                         log: LoggingAdapter): CloseMode = {
    def renderResponseStart(response: HttpResponse, allowUserContentType: Boolean,
                            contentLengthDefined: Boolean): Boolean = {
      def render(h: HttpHeader) = r ~~ h ~~ CrLf
      def suppressionWarning(h: HttpHeader, msg: String = "the spray-can HTTP layer sets this header automatically!"): Unit =
        log.warning("Explicitly set response header '{}' is ignored, {}", h, msg)

      def shouldClose(contentLengthDefined: Boolean, connectionHeader: Connection) =
        ctx.closeAfterResponseCompletion || // request wants to close
          (connectionHeader != null && connectionHeader.hasClose) || // application wants to close
          (chunkless && !contentLengthDefined) // missing content-length, close needed as data boundary

      @tailrec def renderHeaders(remaining: List[HttpHeader], contentLengthDefined: Boolean,
                                 userContentType: Boolean = false, connHeader: Connection = null): Boolean =
        remaining match {
          case head :: tail ⇒ head match {
            case x: `Content-Type` ⇒
              val userCT =
                if (userContentType) { suppressionWarning(x, "another `Content-Type` header was already rendered"); true }
                else if (!allowUserContentType) { suppressionWarning(x, "the response Content-Type is set via the response's HttpEntity!"); false }
                else { render(x); true }
              renderHeaders(tail, contentLengthDefined, userContentType = userCT, connHeader)

            case x: `Content-Length` ⇒
              if (contentLengthDefined) { suppressionWarning(x, "another `Content-Length` header was already rendered"); true }
              else { render(x); true }
              renderHeaders(tail, true, userContentType, connHeader)

            case `Transfer-Encoding`(_) | Date(_) | Server(_) ⇒
              suppressionWarning(head)
              renderHeaders(tail, contentLengthDefined, userContentType, connHeader)

            case x: `Connection` ⇒
              val connectionHeader = if (connHeader eq null) x else Connection(x.tokens ++ connHeader.tokens)
              renderHeaders(tail, contentLengthDefined, userContentType, connectionHeader)

            case x: RawHeader if x.lowercaseName == "content-type" ||
              x.lowercaseName == "content-length" ||
              x.lowercaseName == "transfer-encoding" ||
              x.lowercaseName == "date" ||
              x.lowercaseName == "server" ||
              x.lowercaseName == "connection" ⇒
              suppressionWarning(x, "illegal RawHeader")
              renderHeaders(tail, contentLengthDefined, userContentType, connHeader)

            case x ⇒
              render(x)
              renderHeaders(tail, contentLengthDefined, userContentType, connHeader)
          }

          case Nil ⇒
            response.entity match {
              case HttpEntity.NonEmpty(ContentTypes.NoContentType, _) ⇒
              case HttpEntity.NonEmpty(contentType, _) if !userContentType ⇒ r ~~ `Content-Type` ~~ contentType ~~ CrLf
              case _ ⇒
            }

            shouldClose(contentLengthDefined, connHeader)
        }
      import response._
      if (status eq StatusCodes.OK) r ~~ DefaultStatusLine else r ~~ StatusLineStart ~~ status ~~ CrLf
      r ~~ serverAndDateHeader
      renderHeaders(headers, contentLengthDefined)
    }

    def renderResponse(response: HttpResponse): Boolean = {
      import response._
      val close = renderResponseStart(response,
        allowUserContentType = entity.isEmpty && ctx.requestMethod == HttpMethods.HEAD,
        contentLengthDefined = true)
      renderConnectionHeader(close)

      // don't set a Content-Length header for non-keep-alive HTTP/1.0 responses (rely on body end by connection close)
      if (response.protocol == `HTTP/1.1` || !close || ctx.requestMethod == HttpMethods.HEAD)
        r ~~ `Content-Length` ~~ entity.data.length ~~ CrLf
      r ~~ CrLf
      if (entity.nonEmpty && ctx.requestMethod != HttpMethods.HEAD) r ~~ entity.data
      close
    }

    def renderChunkedResponseStart(response: HttpResponse): CloseMode = {
      val close = renderResponseStart(response,
        allowUserContentType = response.entity.isEmpty,
        contentLengthDefined = false)
      renderConnectionHeader(close)

      if (!chunkless) r ~~ `Transfer-Encoding` ~~ Chunked ~~ CrLf
      r ~~ CrLf
      if (ctx.requestMethod != HttpMethods.HEAD)
        response.entity match {
          case HttpEntity.Empty             ⇒ // nothing to do
          case HttpEntity.NonEmpty(_, data) ⇒ if (chunkless) r ~~ data else r ~~ MessageChunk(data)
        }
      if (close) CloseMode.CloseAfterEnd else CloseMode.DontClose
    }

    def renderConnectionHeader(close: Boolean): Unit =
      ctx.requestProtocol match {
        case `HTTP/1.0` if !close ⇒ r ~~ Connection ~~ KeepAlive ~~ CrLf
        case `HTTP/1.1` if close  ⇒ r ~~ Connection ~~ Close ~~ CrLf
        case _                    ⇒ // no need for rendering
      }

    def chunkless = chunklessStreaming || (ctx.requestProtocol eq `HTTP/1.0`)

    ctx.responsePart match {
      case x: HttpResponse         ⇒ CloseMode.closeNowIf(renderResponse(x))
      case x: ChunkedResponseStart ⇒ renderChunkedResponseStart(x.response)
      case x: MessageChunk ⇒
        if (ctx.requestMethod != HttpMethods.HEAD)
          if (chunkless) r ~~ x.data else r ~~ x
        CloseMode.DontClose
      case x: ChunkedMessageEnd ⇒
        if (ctx.requestMethod != HttpMethods.HEAD && !chunkless) r ~~ x
        CloseMode.closeNowIf(ctx.closeAfterResponseCompletion)
    }
  }

  // for max perf we cache the ServerAndDateHeader of the last second here
  @volatile private[this] var cachedServerAndDateHeader: (Long, Array[Byte]) = _

  private def serverAndDateHeader: Array[Byte] = {
    var (cachedSeconds, cachedBytes) = if (cachedServerAndDateHeader != null) cachedServerAndDateHeader else (0L, null)
    val now = System.currentTimeMillis
    if (now / 1000 != cachedSeconds) {
      cachedSeconds = now / 1000
      val r = new ByteArrayRendering(serverHeaderPlusDateColonSP.length + 31)
      dateTime(now).renderRfc1123DateTimeString(r ~~ serverHeaderPlusDateColonSP) ~~ CrLf
      cachedBytes = r.get
      cachedServerAndDateHeader = cachedSeconds -> cachedBytes
    }
    cachedBytes
  }

  protected def dateTime(now: Long) = DateTime(now) // split out so we can stabilize by overriding in tests

  sealed trait CloseMode {
    def shouldCloseNow(part: HttpResponsePart, closeAfterEnd: Boolean): Boolean
  }
  object CloseMode {
    case object DontClose extends CloseMode {
      def shouldCloseNow(part: HttpResponsePart, closeAfterEnd: Boolean): Boolean =
        closeAfterEnd && part.isInstanceOf[HttpMessageEnd]
    }
    case object CloseNow extends CloseMode {
      def shouldCloseNow(part: HttpResponsePart, closeAfterEnd: Boolean): Boolean = true
    }
    case object CloseAfterEnd extends CloseMode {
      def shouldCloseNow(part: HttpResponsePart, closeAfterEnd: Boolean): Boolean =
        part.isInstanceOf[HttpMessageEnd]
    }

    def closeNowIf(doClose: Boolean): CloseMode = if (doClose) CloseNow else DontClose
  }
}
