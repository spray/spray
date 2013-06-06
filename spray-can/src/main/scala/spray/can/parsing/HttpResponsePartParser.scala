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
import HttpHeaders._
import StatusCodes._
import CharUtils._

class HttpResponsePartParser(_settings: ParserSettings)(_headerParser: HttpHeaderParser = HttpHeaderParser(_settings))
    extends HttpMessagePartParser[HttpResponsePart](_settings, _headerParser) {

  private[this] var isResponseToHeadRequest: Boolean = false
  private[this] var statusCode: StatusCode = StatusCodes.OK

  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit): HttpResponsePartParser =
    new HttpResponsePartParser(settings)(headerParser.copyWith(warnOnIllegalHeader))

  def startResponse(requestMethod: HttpMethod): Unit = {
    isResponseToHeadRequest = requestMethod == HttpMethods.HEAD
    parse = super.apply
  }

  override def apply(input: ByteString): Result[HttpResponsePart] = fail("Unexpected server response")

  def parseMessage(input: ByteString): Result[HttpResponsePart] = {
    var cursor = parseProtocol(input)
    if (byteChar(input, cursor) == ' ') {
      cursor = parseStatusCode(input, cursor + 1)
      cursor = parseReason(input, cursor)()
      parseHeaderLines(input, cursor) match {
        case _: Result.Expect100Continue ⇒ fail("'Expect: 100-continue' is not allowed in HTTP responses")
        case result                      ⇒ result
      }
    } else badProtocol
  }

  def badProtocol = throw new ParsingException("The server-side HTTP version is not supported")

  def parseStatusCode(input: ByteString, cursor: Int): Int = {
    def badStatusCode = throw new ParsingException("Illegal response status code")
    def intValue(offset: Int): Int = {
      val c = byteChar(input, cursor + offset)
      if (isDigit(c)) c - '0' else badStatusCode
    }
    if (byteChar(input, cursor + 3) == ' ') {
      val code = intValue(0) * 100 + intValue(1) * 10 + intValue(2)
      statusCode =
        if (code != 200) StatusCodes.getForKey(code) match {
          case Some(x) ⇒ x
          case None    ⇒ badStatusCode
        }
        else StatusCodes.OK
      cursor + 4
    } else badStatusCode
  }

  @tailrec private def parseReason(input: ByteString, startIx: Int)(cursor: Int = startIx): Int =
    if (cursor - startIx <= settings.maxResponseReasonLength)
      if (byteChar(input, cursor) == '\r' && byteChar(input, cursor + 1) == '\n') cursor + 2
      else parseReason(input, startIx)(cursor + 1)
    else throw new ParsingException("Response reason phrase exceeds the configured limit of " +
      settings.maxResponseReasonLength + " characters")

  // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3
  def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[`Content-Length`],
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`],
                  closeAfterResponseCompletion: Boolean): Result[HttpResponsePart] = {
    def entityExpected: Boolean =
      !isResponseToHeadRequest && {
        statusCode match {
          case _: Informational | NoContent | NotModified ⇒ false
          case _ ⇒ true
        }
      }

    if (entityExpected) {
      teh match {
        case Some(te) if te.encodings.size == 1 && te.hasChunked ⇒
          if (clh.isEmpty) {
            parse = parseChunk(closeAfterResponseCompletion)
            Result.Ok(ChunkedResponseStart(message(headers, EmptyEntity)), drop(input, bodyStart), closeAfterResponseCompletion)
          } else fail("A chunked request must not contain a Content-Length header.")

        case Some(te) ⇒ fail(te.toString + " is not supported by this client")

        case None ⇒ clh match {
          case Some(`Content-Length`(contentLength)) ⇒
            if (contentLength == 0) {
              parse = this
              Result.Ok(message(headers, EmptyEntity), drop(input, bodyStart), closeAfterResponseCompletion)
            } else if (contentLength <= settings.maxContentLength)
              parseFixedLengthBody(headers, input, bodyStart, contentLength, cth, closeAfterResponseCompletion)
            else fail("Response Content-Length " + contentLength + " exceeds the configured limit of " +
              settings.maxContentLength)

          case None ⇒ parseToCloseBody(headers, input, bodyStart, cth)
        }
      }
    } else {
      parse = this
      Result.Ok(message(headers, EmptyEntity), drop(input, bodyStart), closeAfterResponseCompletion)
    }
  }

  def parseToCloseBody(headers: List[HttpHeader], input: ByteString, bodyStart: Int,
                       cth: Option[`Content-Type`]): Result[HttpResponse] = {
    if (input.length - bodyStart <= settings.maxContentLength) {
      parse = { more ⇒
        if (more.isEmpty) {
          parse = this
          val part = message(headers, entity(cth, input.iterator.drop(bodyStart).toArray[Byte]))
          Result.Ok(part, ByteString.empty, closeAfterResponseCompletion = true)
        } else parseToCloseBody(headers, input ++ more, bodyStart, cth)
      }
      Result.NeedMoreData
    } else fail("Response entity exceeds the configured limit of " + settings.maxContentLength + " bytes")
  }

  def message(headers: List[HttpHeader], entity: HttpEntity): HttpResponse =
    HttpResponse(statusCode, entity, headers, protocol)
}
