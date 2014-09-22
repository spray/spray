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

package spray.can.parsing

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import scala.annotation.tailrec
import akka.util.ByteString
import spray.http._
import HttpMethods._
import StatusCodes._
import HttpHeaders._
import CharUtils._

private[can] class HttpRequestPartParser(_settings: ParserSettings, rawRequestUriHeader: Boolean = false)(_headerParser: HttpHeaderParser = HttpHeaderParser(_settings))
    extends HttpMessagePartParser(_settings, _headerParser) {

  private[this] var method: HttpMethod = GET
  private[this] var uri: Uri = Uri.Empty
  private[this] var uriBytes: Array[Byte] = Array()

  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit): HttpRequestPartParser =
    new HttpRequestPartParser(settings, rawRequestUriHeader)(headerParser.copyWith(warnOnIllegalHeader))

  def parseMessage(input: ByteString, offset: Int): Result = {
    var cursor = parseMethod(input, offset)
    cursor = parseRequestTarget(input, cursor)
    cursor = parseProtocol(input, cursor)
    if (byteChar(input, cursor) == '\r' && byteChar(input, cursor + 1) == '\n')
      parseHeaderLines(input, cursor + 2)
    else badProtocol
  }

  def parseMethod(input: ByteString, cursor: Int): Int = {
    @tailrec def parseCustomMethod(ix: Int = 0, sb: JStringBuilder = new JStringBuilder(16)): Int =
      if (ix < 16) { // hard-coded maximum custom method length
        byteChar(input, cursor + ix) match {
          case ' ' ⇒
            HttpMethods.getForKey(sb.toString) match {
              case Some(m) ⇒ { method = m; cursor + ix + 1 }
              case None    ⇒ parseCustomMethod(Int.MaxValue, sb)
            }
          case c ⇒ parseCustomMethod(ix + 1, sb.append(c))
        }
      } else throw new ParsingException(NotImplemented, ErrorInfo("Unsupported HTTP method", sb.toString))

    @tailrec def parseMethod(meth: HttpMethod, ix: Int = 1): Int =
      if (ix == meth.value.length)
        if (byteChar(input, cursor + ix) == ' ') {
          method = meth
          cursor + ix + 1
        } else parseCustomMethod()
      else if (byteChar(input, cursor + ix) == meth.value.charAt(ix)) parseMethod(meth, ix + 1)
      else parseCustomMethod()

    byteChar(input, cursor) match {
      case 'G' ⇒ parseMethod(GET)
      case 'P' ⇒ byteChar(input, cursor + 1) match {
        case 'O' ⇒ parseMethod(POST, 2)
        case 'U' ⇒ parseMethod(PUT, 2)
        case 'A' ⇒ parseMethod(PATCH, 2)
        case _   ⇒ parseCustomMethod()
      }
      case 'D' ⇒ parseMethod(DELETE)
      case 'H' ⇒ parseMethod(HEAD)
      case 'O' ⇒ parseMethod(OPTIONS)
      case 'T' ⇒ parseMethod(TRACE)
      case 'C' ⇒ parseMethod(CONNECT)
      case _   ⇒ parseCustomMethod()
    }
  }

  def parseRequestTarget(input: ByteString, cursor: Int): Int = {
    val uriStart = cursor
    val uriEndLimit = cursor + settings.maxUriLength

    @tailrec def findUriEnd(ix: Int = cursor): Int =
      if (ix == input.length) throw NotEnoughDataException
      else if (CharUtils.isWhitespaceOrNewline(input(ix).toChar)) ix
      else if (ix < uriEndLimit) findUriEnd(ix + 1)
      else throw new ParsingException(RequestUriTooLong,
        s"URI length exceeds the configured limit of ${settings.maxUriLength} characters")

    val uriEnd = findUriEnd()
    try {
      uriBytes = input.iterator.slice(uriStart, uriEnd).toArray[Byte]
      uri = Uri.parseHttpRequestTarget(uriBytes, mode = settings.uriParsingMode)
    } catch {
      case e: IllegalUriException ⇒ throw new ParsingException(BadRequest, e.info)
    }
    uriEnd + 1
  }

  def badProtocol = throw new ParsingException(HTTPVersionNotSupported)

  // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3
  def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[`Content-Length`],
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`], hostHeaderPresent: Boolean,
                  closeAfterResponseCompletion: Boolean): Result =
    if (hostHeaderPresent || protocol == HttpProtocols.`HTTP/1.0`) {
      teh match {
        case Some(te) if te.encodings.size == 1 && te.hasChunked ⇒
          if (clh.isEmpty)
            emit(chunkStartMessage(headers), closeAfterResponseCompletion) {
              parseChunk(input, bodyStart, closeAfterResponseCompletion)
            }
          else fail("A chunked request must not contain a Content-Length header.")

        case Some(te) ⇒ fail(NotImplemented, s"$te is not supported by this server")

        case None ⇒
          val contentLength = clh match {
            case Some(`Content-Length`(len)) ⇒ len
            case None                        ⇒ 0
          }
          if (contentLength == 0)
            emit(message(headers, HttpEntity.Empty), closeAfterResponseCompletion) {
              parseMessageSafe(input, bodyStart)
            }
          else if (contentLength <= settings.maxContentLength)
            parseFixedLengthBody(headers, input, bodyStart, contentLength, cth, closeAfterResponseCompletion)
          else fail(RequestEntityTooLarge, s"Request Content-Length $contentLength exceeds the configured limit of " +
            settings.maxContentLength)
      }
    } else fail("Request is missing required `Host` header")

  def message(headers: List[HttpHeader], entity: HttpEntity) = {
    val requestHeaders =
      if (rawRequestUriHeader) `Raw-Request-URI`(new String(uriBytes, spray.util.US_ASCII)) :: headers else headers
    HttpRequest(method, uri, requestHeaders, entity, protocol)
  }
  def chunkStartMessage(headers: List[HttpHeader]) = ChunkedRequestStart(message(headers, HttpEntity.Empty))
}
