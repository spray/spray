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

package spray.can
package rendering

import scala.annotation.tailrec
import spray.util._
import spray.http._
import HttpProtocols._
import HttpHeaders._
import RenderSupport._
import akka.event.LoggingAdapter

trait ResponseRenderingComponent {
  def serverHeaderValue: String
  def chunklessStreaming: Boolean

  private[this] val serverHeaderPlusDateColonSP =
    serverHeaderValue match {
      case "" ⇒ "Date: ".getAsciiBytes
      case x  ⇒ ("Server: " + x + "\r\nDate: ").getAsciiBytes
    }

  def renderResponsePartRenderingContext(r: Rendering, ctx: ResponsePartRenderingContext,
                                         log: LoggingAdapter): Boolean = {
    def renderResponseStart(response: HttpResponse): Connection = {
      @tailrec def renderHeaders(remaining: List[HttpHeader])(connectionHeader: Connection = null): Connection =
        remaining match {
          case Nil ⇒ connectionHeader
          case head :: tail ⇒ renderHeaders(tail) {
            def logHeaderSuppressionWarning(msg: String = "the spray-can HTTP layer sets this header automatically!"): Connection = {
              log.warning("Explicitly set response header '{}' is ignored, {}", head, msg)
              connectionHeader
            }
            head match {
              case _: `Content-Type` if !response.entity.isEmpty ⇒ logHeaderSuppressionWarning("the response Content-Type is set via the response's HttpEntity!")
              case _: `Content-Length`                           ⇒ logHeaderSuppressionWarning()
              case _: `Transfer-Encoding`                        ⇒ logHeaderSuppressionWarning()
              case _: `Date`                                     ⇒ logHeaderSuppressionWarning()
              case _: `Server`                                   ⇒ logHeaderSuppressionWarning()
              case x: `Connection` ⇒
                r ~~ x ~~ CrLf
                if (connectionHeader eq null) x else Connection(x.tokens ++ connectionHeader.tokens)
              case x: RawHeader if x.lowercaseName == "content-type" ||
                x.lowercaseName == "content-length" ||
                x.lowercaseName == "transfer-encoding" ||
                x.lowercaseName == "date" ||
                x.lowercaseName == "server" ||
                x.lowercaseName == "connection" ⇒ logHeaderSuppressionWarning()
              case _ ⇒ r ~~ head ~~ CrLf; connectionHeader
            }
          }
        }
      import response._
      if (status eq StatusCodes.OK) r ~~ DefaultStatusLine else r ~~ StatusLineStart ~~ status ~~ CrLf
      r ~~ serverAndDateHeader
      entity match {
        case HttpBody(contentType, _) ⇒ r ~~ `Content-Type` ~~ contentType ~~ CrLf
        case EmptyEntity              ⇒
      }
      renderHeaders(headers)()
    }

    def renderResponse(response: HttpResponse): Boolean = {
      import response._
      val connectionHeader = renderResponseStart(response)
      val close =
        ctx.requestProtocol match {
          case `HTTP/1.0` ⇒ if (connectionHeader eq null) {
            if (!ctx.closeAfterResponseCompletion) r ~~ Connection ~~ KeepAlive ~~ CrLf
            ctx.closeAfterResponseCompletion
          } else !connectionHeader.hasKeepAlive
          case `HTTP/1.1` ⇒ if (connectionHeader eq null) {
            if (ctx.closeAfterResponseCompletion) r ~~ Connection ~~ Close ~~ CrLf
            ctx.closeAfterResponseCompletion
          } else connectionHeader.hasClose
        }

      // don't set a Content-Length header for non-keep-alive HTTP/1.0 responses (rely on body end by connection close)
      if (response.protocol == `HTTP/1.1` || !close) r ~~ `Content-Length` ~~ entity.buffer.length ~~ CrLf
      r ~~ CrLf
      if (entity.buffer.length > 0 && ctx.requestMethod != HttpMethods.HEAD) r ~~ entity.buffer
      close
    }

    def renderChunkedResponseStart(response: HttpResponse, chunkless: Boolean): Boolean = {
      renderResponseStart(response)
      if (!chunkless) r ~~ `Transfer-Encoding` ~~ Chunked ~~ CrLf
      r ~~ CrLf
      if (ctx.requestMethod != HttpMethods.HEAD && response.entity.buffer.length > 0) {
        if (chunkless) r ~~ response.entity.buffer else r ~~ MessageChunk(response.entity.buffer)
      }
      false
    }

    def chunkless = chunklessStreaming || (ctx.requestProtocol eq `HTTP/1.0`)

    ctx.responsePart match {
      case x: HttpResponse         ⇒ renderResponse(x)
      case x: ChunkedResponseStart ⇒ renderChunkedResponseStart(x.response, chunkless)
      case x: MessageChunk ⇒
        if (ctx.requestMethod != HttpMethods.HEAD)
          if (chunkless) r ~~ x.body else r ~~ x
        false
      case x: ChunkedMessageEnd ⇒
        if (ctx.requestMethod == HttpMethods.HEAD) ctx.closeAfterResponseCompletion
        else if (chunkless) true
        else {
          r ~~ x
          ctx.closeAfterResponseCompletion
        }
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
}