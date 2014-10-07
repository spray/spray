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

import scala.annotation.tailrec
import akka.util.ByteString
import spray.http._
import HttpHeaders._
import CharUtils._

private[can] class HttpResponsePartParser(_settings: ParserSettings)(_headerParser: HttpHeaderParser = HttpHeaderParser(_settings))
    extends HttpMessagePartParser(_settings, _headerParser) {
  import HttpResponsePartParser.NoMethod

  private[this] var requestMethodForCurrentResponse: HttpMethod = NoMethod
  private[this] var statusCode: StatusCode = StatusCodes.OK

  def copyWith(warnOnIllegalHeader: ErrorInfo ⇒ Unit) =
    new HttpResponsePartParser(settings)(headerParser.copyWith(warnOnIllegalHeader))

  def setRequestMethodForNextResponse(method: HttpMethod): Unit =
    requestMethodForCurrentResponse = method

  def parseMessage(input: ByteString, offset: Int): Result =
    if (input.isEmpty || offset == input.size || (requestMethodForCurrentResponse ne NoMethod)) {
      var cursor = parseProtocol(input, offset)
      if (byteChar(input, cursor) == ' ') {
        cursor = parseStatusCode(input, cursor + 1)
        cursor = parseReason(input, cursor)()
        parseHeaderLines(input, cursor)
      } else badProtocol
    } else fail("Unexpected server response", input.drop(offset).utf8String)

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
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`], hostHeaderPresent: Boolean,
                  closeAfterResponseCompletion: Boolean): Result =
    if (statusCode.allowsEntity && (requestMethodForCurrentResponse ne HttpMethods.HEAD)) {
      teh match {
        case Some(`Transfer-Encoding`(Seq("chunked"))) ⇒
          if (clh.isEmpty) {
            emit(ChunkedResponseStart(message(headers, HttpEntity.Empty)), closeAfterResponseCompletion) {
              parseChunk(input, bodyStart, closeAfterResponseCompletion)
            }
          } else fail("A chunked response must not contain a Content-Length header.")

        case None | Some(`Transfer-Encoding`(Seq("identity"))) ⇒ clh match {
          case Some(`Content-Length`(contentLength)) ⇒
            if (contentLength == 0) {
              emit(message(headers, HttpEntity.Empty), closeAfterResponseCompletion) {
                parseMessageSafe(input, bodyStart)
              }
            } else if (contentLength <= settings.maxContentLength)
              parseFixedLengthBody(headers, input, bodyStart, contentLength, cth, closeAfterResponseCompletion)
            else fail(s"Response Content-Length $contentLength exceeds the configured limit of " +
              settings.maxContentLength)

          case None ⇒ parseToCloseBody(headers, input, bodyStart, cth)
        }

        case Some(te) ⇒ fail(te.toString + " is not supported by this client")
      }
    } else emit(message(headers, HttpEntity.Empty), closeAfterResponseCompletion) {
      parseMessageSafe(input, bodyStart)
    }

  def parseToCloseBody(headers: List[HttpHeader], input: ByteString, bodyStart: Int,
                       cth: Option[`Content-Type`]): Result = {
    val currentBodySize = input.length - bodyStart
    if (currentBodySize <= settings.maxContentLength)
      if (currentBodySize < settings.autoChunkingThreshold)
        Result.NeedMoreData {
          case ByteString.empty ⇒
            emit(message(headers, entity(cth, input drop bodyStart)), closeAfterResponseCompletion = true) {
              Result.IgnoreAllFurtherInput
            }
          case more ⇒ parseToCloseBody(headers, input ++ more, bodyStart, cth)
        }
      else emit(chunkStartMessage(headers), closeAfterResponseCompletion = true) {
        if (currentBodySize > 0)
          emit(MessageChunk(HttpData(input drop bodyStart)), closeAfterResponseCompletion = true)(autoChunkToCloseBody)
        else autoChunkToCloseBody
      }
    else fail(s"Response entity exceeds the configured limit of ${settings.maxContentLength} bytes")
  }

  // could be a val but we save the allocation in the most common case of not having an auto-chunked to-close body
  def autoChunkToCloseBody: Result = Result.NeedMoreData {
    case ByteString.empty ⇒
      emit(ChunkedMessageEnd, closeAfterResponseCompletion = true) { Result.IgnoreAllFurtherInput }
    case more ⇒
      emit(MessageChunk(HttpData(more.compact)), closeAfterResponseCompletion = true)(autoChunkToCloseBody)
  }

  def message(headers: List[HttpHeader], entity: HttpEntity) = HttpResponse(statusCode, entity, headers, protocol)
  def chunkStartMessage(headers: List[HttpHeader]) = ChunkedResponseStart(message(headers, HttpEntity.Empty))
}

private[can] object HttpResponsePartParser {
  val NoMethod = HttpMethod.custom("NONE", safe = false, idempotent = false, entityAccepted = false)
}
