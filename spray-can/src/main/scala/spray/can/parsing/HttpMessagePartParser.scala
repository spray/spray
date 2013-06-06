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
import StatusCodes._
import HttpHeaders._
import HttpProtocols._
import CharUtils._

private[parsing] abstract class HttpMessagePartParser[Part <: HttpMessagePart](val settings: ParserSettings,
                                                                               val headerParser: HttpHeaderParser) extends Parser[Part] {
  var parse: ByteString ⇒ Result[Part] = this
  var protocol: HttpProtocol = `HTTP/1.1`

  def apply(input: ByteString): Result[Part] =
    try parseMessage(input)
    catch {
      case NotEnoughDataException ⇒
        parse = { more ⇒ this((input ++ more).compact) }
        Result.NeedMoreData

      case e: ParsingException ⇒ fail(e.status, e.info)
    }

  def parseMessage(input: ByteString): Result[Part]

  def parseProtocol(input: ByteString, cursor: Int = 0): Int = {
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
                                      teh: Option[`Transfer-Encoding`] = None, e100: Boolean = false): Result[Part] = {
    var lineEnd = 0
    val result =
      try {
        lineEnd = headerParser.parseHeaderLine(input, lineStart)()
        null
      } catch {
        case NotEnoughDataException ⇒
          parse = { more ⇒ parseHeaderLinesAux((input ++ more).compact, lineStart, headers, headerCount, ch, clh, cth, teh, e100) }
          Result.NeedMoreData

        case e: ParsingException ⇒ fail(e.status, e.info)
      }
    if (result != null) result
    else headerParser.resultHeader match {
      case HttpHeaderParser.EmptyHeader ⇒
        val close = closeAfterResponseCompletion(ch)
        if (e100) {
          parse = parseEntity(headers, _, 0, clh, cth, teh, close)
          Result.Expect100Continue(drop(input, lineEnd))
        } else parseEntity(headers, input, lineEnd, clh, cth, teh, close)

      case h: Connection ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, Some(h), clh, cth, teh, e100)

      case h: `Content-Length` ⇒
        if (clh.isEmpty) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, Some(h), cth, teh, e100)
        else fail("HTTP message must not contain more than one Content-Length header")

      case h: `Content-Type` ⇒
        if (cth.isEmpty) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, Some(h), teh, e100)
        else fail("HTTP message must not contain more than one Content-Type header")

      case h: `Transfer-Encoding` ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, Some(h), e100)

      case h: Expect ⇒
        if (h.has100continue) parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, teh, e100 = true)
        else fail(ExpectationFailed, "Expectation '" + h + "' is not supported by this server")

      case h if headerCount < settings.maxHeaderCount ⇒
        parseHeaderLines(input, lineEnd, h :: headers, headerCount + 1, ch, clh, cth, teh, e100)

      case _ ⇒ fail("HTTP message contains more than the configured limit of " + settings.maxHeaderCount + " headers")
    }
  }

  def parseHeaderLinesAux(input: ByteString, lineStart: Int, headers: List[HttpHeader], headerCount: Int,
                          ch: Option[Connection], clh: Option[`Content-Length`], cth: Option[`Content-Type`],
                          teh: Option[`Transfer-Encoding`], e100: Boolean): Result[Part] =
    parseHeaderLines(input, lineStart, headers, headerCount, ch, clh, cth, teh, e100)

  def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[`Content-Length`],
                  cth: Option[`Content-Type`], teh: Option[`Transfer-Encoding`],
                  closeAfterResponseCompletion: Boolean): Result[Part]

  def parseFixedLengthBody(headers: List[HttpHeader], input: ByteString, bodyStart: Int, length: Int,
                           cth: Option[`Content-Type`], closeAfterResponseCompletion: Boolean): Result[Part] =
    if (bodyStart + length <= input.length) {
      parse = this
      val part = message(headers, entity(cth, input.iterator.slice(bodyStart, bodyStart + length).toArray[Byte]))
      Result.Ok(part, drop(input, bodyStart + length), closeAfterResponseCompletion)
    } else {
      parse = more ⇒ parseFixedLengthBody(headers, input ++ more, bodyStart, length, cth, closeAfterResponseCompletion)
      Result.NeedMoreData
    }

  def parseChunk(closeAfterResponseCompletion: Boolean)(input: ByteString): Result[Part] = {
    @tailrec def parseTrailer(extension: String, lineStart: Int, headers: List[HttpHeader] = Nil,
                              headerCount: Int = 0): Result[Part] = {
      val lineEnd = headerParser.parseHeaderLine(input, lineStart)()
      headerParser.resultHeader match {
        case HttpHeaderParser.EmptyHeader ⇒
          parse = this
          Result.Ok(ChunkedMessageEnd(extension, headers).asInstanceOf[Part],
            drop(input, lineEnd), closeAfterResponseCompletion)
        case header if headerCount < settings.maxHeaderCount ⇒
          parseTrailer(extension, lineEnd, header :: headers, headerCount + 1)
        case _ ⇒ fail("Chunk trailer contains more than the configured limit of " + settings.maxHeaderCount + " headers")
      }
    }

    def parseChunkBody(chunkSize: Int, extension: String, cursor: Int): Result[Part] =
      if (chunkSize > 0) {
        val chunkBodyEnd = cursor + chunkSize
        def result(terminatorLen: Int) = {
          parse = parseChunk(closeAfterResponseCompletion)
          val chunk = MessageChunk(input.iterator.slice(cursor, chunkBodyEnd).toArray[Byte], extension)
          Result.Ok(chunk.asInstanceOf[Part], drop(input, chunkBodyEnd + terminatorLen), closeAfterResponseCompletion)
        }
        byteChar(input, chunkBodyEnd) match {
          case '\r' if byteChar(input, chunkBodyEnd + 1) == '\n' ⇒ result(2)
          case '\n' ⇒ result(1)
          case x ⇒ fail("Illegal chunk termination")
        }
      } else parseTrailer(extension, cursor)

    @tailrec def parseChunkExtensions(chunkSize: Int, cursor: Int)(startIx: Int = cursor): Result[Part] =
      if (cursor - startIx <= settings.maxChunkExtLength) {
        def extension = asciiString(input, startIx, cursor)
        byteChar(input, cursor) match {
          case '\r' if byteChar(input, cursor + 1) == '\n' ⇒ parseChunkBody(chunkSize, extension, cursor + 2)
          case '\n' ⇒ parseChunkBody(chunkSize, extension, cursor + 1)
          case _ ⇒ parseChunkExtensions(chunkSize, cursor + 1)(startIx)
        }
      } else fail("HTTP chunk extension length exceeds configured limit of " + settings.maxChunkExtLength + " characters")

    @tailrec def parseSize(cursor: Int = 0, size: Long = 0): Result[Part] =
      if (size <= settings.maxChunkSize) {
        byteChar(input, cursor) match {
          case c if isHexDigit(c) ⇒ parseSize(cursor + 1, size * 16 + hexValue(c))
          case ';' if cursor > 0 ⇒ parseChunkExtensions(size.toInt, cursor + 1)()
          case '\r' if cursor > 0 && byteChar(input, cursor + 1) == '\n' ⇒ parseChunkBody(size.toInt, "", cursor + 2)
          case c ⇒ fail("Illegal character '" + escape(c) + "' in chunk start")
        }
      } else fail("HTTP chunk size exceeds the configured limit of " + settings.maxChunkSize + " bytes")

    try parseSize()
    catch {
      case NotEnoughDataException ⇒
        parse = { more ⇒ parseChunk(closeAfterResponseCompletion)((input ++ more).compact) }
        Result.NeedMoreData

      case e: ParsingException ⇒ fail(e.status, e.info)
    }
  }

  def entity(cth: Option[`Content-Type`], body: Array[Byte]): HttpEntity = {
    val contentType = cth match {
      case Some(x) ⇒ x.contentType
      case None    ⇒ ContentTypes.`application/octet-stream`
    }
    HttpEntity(contentType, body)
  }

  def closeAfterResponseCompletion(connectionHeader: Option[Connection]) =
    protocol match {
      case `HTTP/1.1` ⇒ connectionHeader.isDefined && connectionHeader.get.hasClose
      case `HTTP/1.0` ⇒ connectionHeader.isEmpty || !connectionHeader.get.hasKeepAlive
    }

  def message(headers: List[HttpHeader], entity: HttpEntity): Part

  def drop(input: ByteString, n: Int): ByteString =
    if (input.length == n) ByteString.empty else input.drop(n).compact

  def fail(summary: String): Result[Nothing] = fail(summary, "")
  def fail(summary: String, detail: String): Result[Nothing] = fail(StatusCodes.BadRequest, summary, detail)
  def fail(status: StatusCode): Result[Nothing] = fail(status, status.defaultMessage)
  def fail(status: StatusCode, summary: String, detail: String = ""): Result[Nothing] = fail(status, ErrorInfo(summary, detail))
  def fail(status: StatusCode, info: ErrorInfo): Result[Nothing] = {
    val error = Result.ParsingError(status, info)
    parse = { _ ⇒ error }
    error
  }
}
