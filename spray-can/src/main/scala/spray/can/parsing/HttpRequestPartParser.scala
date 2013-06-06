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

package spray.can.parsing

import scala.annotation.tailrec
import akka.util.ByteString
import spray.http._
import HttpMethods._
import StatusCodes._
import HttpHeaders._
import CharUtils._

class HttpRequestPartParser(_settings: ParserSettings)(_headerParser: HttpHeaderParser = HttpHeaderParser(_settings))
    extends HttpMessagePartParser[HttpRequestPart](_settings, _headerParser) {

  private[this] var method: HttpMethod = GET
  private[this] var uri: Uri = Uri.Empty

  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit): HttpRequestPartParser =
    new HttpRequestPartParser(settings)(headerParser.copyWith(warnOnIllegalHeader))

  def parseMessage(input: ByteString): Result[HttpRequestPart] = {
    var cursor = parseMethod(input)
    cursor = parseRequestTarget(input, cursor)
    cursor = parseProtocol(input, cursor)
    if (byteChar(input, cursor) == '\r' && byteChar(input, cursor + 1) == '\n')
      parseHeaderLines(input, cursor + 2)
    else badProtocol
  }

  def parseMethod(input: ByteString): Int = {
    def badMethod = throw new ParsingException(NotImplemented, ErrorInfo("Unsupported HTTP method"))
    @tailrec def parseMethod(meth: HttpMethod, ix: Int = 1): Int =
      if (ix == meth.value.length)
        if (byteChar(input, ix) == ' ') {
          method = meth
          ix + 1
        } else badMethod
      else if (byteChar(input, ix) == meth.value.charAt(ix)) parseMethod(meth, ix + 1)
      else badMethod

    byteChar(input, 0) match {
      case 'G' ⇒ parseMethod(GET)
      case 'P' ⇒ byteChar(input, 1) match {
        case 'O' ⇒ parseMethod(POST, 2)
        case 'U' ⇒ parseMethod(PUT, 2)
        case 'A' ⇒ parseMethod(PATCH, 2)
        case _   ⇒ badMethod
      }
      case 'D' ⇒ parseMethod(DELETE)
      case 'H' ⇒ parseMethod(HEAD)
      case 'O' ⇒ parseMethod(OPTIONS)
      case 'T' ⇒ parseMethod(TRACE)
      case _   ⇒ badMethod
    }
  }

  def parseRequestTarget(input: ByteString, cursor: Int): Int = {
    val uriStart = cursor
    val uriEndLimit = cursor + settings.maxUriLength

    @tailrec def findUriEnd(ix: Int = cursor): Int =
      if (ix == input.length) throw NotEnoughDataException
      else if (input(ix) == ' ') ix
      else if (ix < uriEndLimit) findUriEnd(ix + 1)
      else throw new ParsingException(RequestUriTooLong,
        "URI length exceeds the configured limit of " + settings.maxUriLength + " characters")

    val uriEnd = findUriEnd()
    try {
      val uriBytes = input.iterator.slice(uriStart, uriEnd).toArray[Byte]
      uri = Uri.parseHttpRequestTarget(uriBytes, mode = settings.uriParsingMode)
    } catch {
      case e: IllegalUriException ⇒
        throw new ParsingException(BadRequest, e.info.withSummaryPrepended("Illegal request URI"))
    }
    uriEnd + 1
  }

  def badProtocol = throw new ParsingException(HTTPVersionNotSupported)

  // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3
  def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[`Content-Length`],
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`],
                  closeAfterResponseCompletion: Boolean): Result[HttpRequestPart] =
    teh match {
      case Some(te) if te.encodings.size == 1 && te.hasChunked ⇒
        if (clh.isEmpty) {
          parse = parseChunk(closeAfterResponseCompletion)
          Result.Ok(ChunkedRequestStart(message(headers, EmptyEntity)), drop(input, bodyStart), closeAfterResponseCompletion)
        } else fail("A chunked request must not contain a Content-Length header.")

      case Some(te) ⇒ fail(NotImplemented, te + " is not supported by this server")

      case None ⇒
        val contentLength = clh match {
          case Some(`Content-Length`(len)) ⇒ len
          case None                        ⇒ 0
        }
        if (contentLength == 0) {
          parse = this
          Result.Ok(message(headers, EmptyEntity), drop(input, bodyStart), closeAfterResponseCompletion)
        } else if (contentLength <= settings.maxContentLength)
          parseFixedLengthBody(headers, input, bodyStart, contentLength, cth, closeAfterResponseCompletion)
        else fail(RequestEntityTooLarge, "Request Content-Length " + contentLength + " exceeds the configured limit of " +
          settings.maxContentLength)
    }

  def message(headers: List[HttpHeader], entity: HttpEntity) =
    HttpRequest(method, uri, headers, entity, protocol)
}
