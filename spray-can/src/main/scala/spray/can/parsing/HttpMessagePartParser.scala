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
import StatusCodes._
import HttpHeaders._
import HttpProtocols._
import CharUtils._

private[parsing] abstract class HttpMessagePartParser(val settings: ParserSettings,
                                                      val headerParser: HttpHeaderParser) extends Parser {
  protected var protocol: HttpProtocol = `HTTP/1.1`

  def apply(input: ByteString): Result = parseMessageSafe(input)

  def parseMessageSafe(input: ByteString, offset: Int = 0): Result = {
    def needMoreData = this.needMoreData(input, offset)(parseMessageSafe)
    if (input.length > offset)
      try parseMessage(input, offset)
      catch {
        case NotEnoughDataException ⇒ needMoreData
        case e: ParsingException    ⇒ fail(e.status, e.info)
      }
    else needMoreData
  }

  def parseMessage(input: ByteString, offset: Int): Result

  def parseProtocol(input: ByteString, cursor: Int): Int = {
    def c(ix: Int) = byteChar(input, cursor + ix)
    if (c(0) == 'H' && c(1) == 'T' && c(2) == 'T' && c(3) == 'P' && c(4) == '/' && c(5) == '1' && c(6) == '.') {
      protocol = c(7) match {
        case '0' ⇒ `HTTP/1.0`
        case '1' ⇒ `HTTP/1.1`
        case _   ⇒ badProtocol
      }
      cursor + 8
    } else badProtocol
  }

  def badProtocol: Nothing

  @tailrec final def parseHeaderLines(input: ByteString, lineStart: Int, headers: List[HttpHeader] = Nil,
                                      headerCount: Int = 0, ch: Option[Connection] = None,
                                      clh: Option[`Content-Length`] = None, cth: Option[`Content-Type`] = None,
                                      teh: Option[`Transfer-Encoding`] = None, e100: Boolean = false,
                                      hh: Boolean = false): Result = {
    var lineEnd = 0
    val result: Result =
      try {
        lineEnd = headerParser.parseHeaderLine(input, lineStart)()
        null
      } catch {
        case NotEnoughDataException ⇒
          needMoreData(input, lineStart)(parseHeaderLinesAux(_, _, headers, headerCount, ch, clh, cth, teh, e100, hh))
        case e: ParsingException ⇒ fail(e.status, e.info)
      }
    if (result != null) result
    else headerParser.resultHeader match {
      case HttpHeaderParser.EmptyHeader ⇒
        val close = HttpMessage.connectionCloseExpected(protocol, ch)
        val next = parseEntity(headers, input, lineEnd, clh, cth, teh, hh, close)
        if (e100) Result.Expect100Continue(() ⇒ next) else next

      case h: Connection ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, Some(h), clh, cth, teh, e100, hh)

      case h: `Content-Length` ⇒
        if (clh.isEmpty) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, Some(h), cth, teh, e100, hh)
        else fail("HTTP message must not contain more than one Content-Length header")

      case h: `Content-Type` ⇒
        if (cth.isEmpty) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, Some(h), teh, e100, hh)
        else if (cth.get == h) parseHeaderLines(input, lineEnd, headers, headerCount, ch, clh, cth, teh, e100, hh)
        else fail("HTTP message must not contain more than one Content-Type header")

      case h: `Transfer-Encoding` ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, Some(h), e100, hh)

      case h: Expect ⇒
        if (h.has100continue) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, teh, e100 = true, hh)
        else fail(ExpectationFailed, s"Expectation '$h' is not supported by this server")

      case h if headerCount < settings.maxHeaderCount ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, teh, e100, hh || h.isInstanceOf[Host])

      case _ ⇒ fail(s"HTTP message contains more than the configured limit of ${settings.maxHeaderCount} headers")
    }
  }

  // work-around for compiler bug complaining about non-tail-recursion if we inline this method
  def parseHeaderLinesAux(input: ByteString, lineStart: Int, headers: List[HttpHeader], headerCount: Int,
                          ch: Option[Connection], clh: Option[`Content-Length`], cth: Option[`Content-Type`],
                          teh: Option[`Transfer-Encoding`], e100: Boolean, hh: Boolean): Result =
    parseHeaderLines(input, lineStart, headers, headerCount, ch, clh, cth, teh, e100, hh)

  def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[`Content-Length`],
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`], hostHeaderPresent: Boolean,
                  closeAfterResponseCompletion: Boolean): Result

  def parseFixedLengthBody(headers: List[HttpHeader], input: ByteString, bodyStart: Int, length: Long,
                           cth: Option[`Content-Type`], closeAfterResponseCompletion: Boolean): Result =
    if (length >= settings.autoChunkingThreshold) {
      emit(chunkStartMessage(headers), closeAfterResponseCompletion) {
        parseBodyWithAutoChunking(input, bodyStart, length, closeAfterResponseCompletion)
      }
    } else if (length > Int.MaxValue) {
      fail("Content-Length > Int.MaxSize not supported for non-(auto)-chunked requests")
    } else if (bodyStart.toLong + length <= input.length) {
      val offset = bodyStart + length.toInt
      val msg = message(headers, entity(cth, input.slice(bodyStart, offset)))
      emit(msg, closeAfterResponseCompletion) {
        if (input.isCompact) parseMessageSafe(input, offset)
        else parseMessageSafe(input.drop(offset))
      }
    } else needMoreData(input, bodyStart)(parseFixedLengthBody(headers, _, _, length, cth, closeAfterResponseCompletion))

  def parseChunk(input: ByteString, offset: Int, closeAfterResponseCompletion: Boolean): Result = {
    @tailrec def parseTrailer(extension: String, lineStart: Int, headers: List[HttpHeader] = Nil,
                              headerCount: Int = 0): Result = {
      val lineEnd = headerParser.parseHeaderLine(input, lineStart)()
      headerParser.resultHeader match {
        case HttpHeaderParser.EmptyHeader ⇒
          emit(ChunkedMessageEnd(extension, headers), closeAfterResponseCompletion) { parseMessageSafe(input, lineEnd) }
        case header if headerCount < settings.maxHeaderCount ⇒
          parseTrailer(extension, lineEnd, header :: headers, headerCount + 1)
        case _ ⇒ fail(s"Chunk trailer contains more than the configured limit of ${settings.maxHeaderCount} headers")
      }
    }

    def parseChunkBody(chunkSize: Int, extension: String, cursor: Int): Result =
      if (chunkSize > 0) {
        val chunkBodyEnd = cursor + chunkSize
        def result(terminatorLen: Int) = {
          val chunk = MessageChunk(HttpData(input.slice(cursor, chunkBodyEnd)), extension)
          emit(chunk, closeAfterResponseCompletion) {
            parseChunk(input, chunkBodyEnd + terminatorLen, closeAfterResponseCompletion)
          }
        }
        byteChar(input, chunkBodyEnd) match {
          case '\r' if byteChar(input, chunkBodyEnd + 1) == '\n' ⇒ result(2)
          case '\n' ⇒ result(1)
          case x ⇒ fail("Illegal chunk termination")
        }
      } else parseTrailer(extension, cursor)

    @tailrec def parseChunkExtensions(chunkSize: Int, cursor: Int)(startIx: Int = cursor): Result =
      if (cursor - startIx <= settings.maxChunkExtLength) {
        def extension = asciiString(input, startIx, cursor)
        byteChar(input, cursor) match {
          case '\r' if byteChar(input, cursor + 1) == '\n' ⇒ parseChunkBody(chunkSize, extension, cursor + 2)
          case '\n' ⇒ parseChunkBody(chunkSize, extension, cursor + 1)
          case _ ⇒ parseChunkExtensions(chunkSize, cursor + 1)(startIx)
        }
      } else fail(s"HTTP chunk extension length exceeds configured limit of ${settings.maxChunkExtLength} characters")

    @tailrec def parseSize(cursor: Int = offset, size: Long = 0): Result =
      if (size <= settings.maxChunkSize) {
        byteChar(input, cursor) match {
          case c if isHexDigit(c) ⇒ parseSize(cursor + 1, size * 16 + hexValue(c))
          case ';' if cursor > offset ⇒ parseChunkExtensions(size.toInt, cursor + 1)()
          case '\r' if cursor > offset && byteChar(input, cursor + 1) == '\n' ⇒ parseChunkBody(size.toInt, "", cursor + 2)
          case c ⇒ fail(s"Illegal character '${escape(c)}' in chunk start")
        }
      } else fail(s"HTTP chunk size exceeds the configured limit of ${settings.maxChunkSize} bytes")

    try parseSize()
    catch {
      case NotEnoughDataException ⇒ needMoreData(input, offset)(parseChunk(_, _, closeAfterResponseCompletion))
      case e: ParsingException    ⇒ fail(e.status, e.info)
    }
  }

  def parseBodyWithAutoChunking(input: ByteString, offset: Int, remainingBytes: Long,
                                closeAfterResponseCompletion: Boolean): Result = {
    require(remainingBytes > 0)
    val chunkSize = math.min(remainingBytes, input.size - offset).toInt // safe conversion because input.size returns an Int
    if (chunkSize > 0) {
      val chunkEnd = offset + chunkSize
      val chunk = MessageChunk(HttpData(input.slice(offset, chunkEnd).compact))
      emit(chunk, closeAfterResponseCompletion) {
        if (chunkSize == remainingBytes) // last chunk
          emit(ChunkedMessageEnd, closeAfterResponseCompletion) {
            if (input.isCompact) parseMessageSafe(input, chunkEnd)
            else parseMessageSafe(input.drop(chunkEnd))
          }
        else parseBodyWithAutoChunking(input, chunkEnd, remainingBytes - chunkSize, closeAfterResponseCompletion)
      }
    } else needMoreData(input, offset)(parseBodyWithAutoChunking(_, _, remainingBytes, closeAfterResponseCompletion))
  }

  def entity(cth: Option[`Content-Type`], body: ByteString): HttpEntity = {
    val contentType = cth match {
      case Some(x) ⇒ x.contentType
      case None    ⇒ ContentTypes.`application/octet-stream`
    }
    HttpEntity(contentType, HttpData(body.compact))
  }

  def needMoreData(input: ByteString, offset: Int)(next: (ByteString, Int) ⇒ Result): Result =
    if (offset == input.length) Result.NeedMoreData(next(_, 0))
    else Result.NeedMoreData(more ⇒ next(input ++ more, offset))

  def emit(part: HttpMessagePart, closeAfterResponseCompletion: Boolean)(continue: ⇒ Result) =
    Result.Emit(part, closeAfterResponseCompletion, () ⇒ continue)

  def fail(summary: String): Result = fail(summary, "")
  def fail(summary: String, detail: String): Result = fail(StatusCodes.BadRequest, summary, detail)
  def fail(status: StatusCode): Result = fail(status, status.defaultMessage)
  def fail(status: StatusCode, summary: String, detail: String = ""): Result = fail(status, ErrorInfo(summary, detail))
  def fail(status: StatusCode, info: ErrorInfo) = Result.ParsingError(status, info)

  def message(headers: List[HttpHeader], entity: HttpEntity): HttpMessagePart
  def chunkStartMessage(headers: List[HttpHeader]): HttpMessageStart
}
