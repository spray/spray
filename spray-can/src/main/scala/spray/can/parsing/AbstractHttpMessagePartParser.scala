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

sealed trait ParsingResult
object ParsingResult {
  case object NeedMoreData extends ParsingResult
  case class Ok(part: HttpMessagePart) extends ParsingResult
  case class OkAndContinue(part: HttpMessagePart, remainingData: ByteString) extends ParsingResult
}

trait HttpMessagePartParser[T <: HttpMessagePart] {
  def run: ByteString ⇒ ParsingResult
}

class ParsingException(status: StatusCode, info: ErrorInfo) extends RuntimeException(info.formatPretty)

private[parsing] object HttpMessagePartParser {
  sealed trait MessageLine
  case class RequestLine(method: HttpMethod, uri: Uri, protocol: HttpProtocol) extends MessageLine
  case class StatusLine(protocol: HttpProtocol,
                        status: Int,
                        reason: String,
                        isResponseToHeadRequest: Boolean = false) extends MessageLine

  object NotEnoughDataException extends RuntimeException {
    override def fillInStackTrace(): Throwable = this // suppress stack trace creation
  }
}

private[parsing] abstract class AbstractHttpMessagePartParser[T <: HttpMessagePart](val settings: ParserSettings,
                                                                                    headerParser: HttpHeaderParser)
    extends HttpMessagePartParser[T]
    with (ByteString ⇒ ParsingResult)
    with (() ⇒ Char) {

  import HttpMessagePartParser._

  type ML <: MessageLine
  var run: ByteString ⇒ ParsingResult = this
  var input = ByteString.empty
  var cursor = 0
  var chunkExpected = false

  def apply(data: ByteString): ParsingResult =
    if (data.nonEmpty) {
      input = data
      cursor = 0
      if (chunkExpected) messageChunk() else messageLine()
    } else ParsingResult.NeedMoreData

  def apply(): Char = nextChar()

  def messageLine(): ParsingResult

  def headerLines(messageLine: ML, headers: List[HttpHeader] = Nil, headerCount: Int = 0): ParsingResult = {
    val lineStart = cursor
    try {
      val (header, endIx) = headerParser.parseHeaderLine(charSequenceAdapter(lineStart))
      cursor = lineStart + endIx
      if (HttpHeaderParser.EmptyHeader == header) body(messageLine, headers)
      else if (headerCount < settings.maxHeaderCount) headerLines(messageLine, header :: headers, headerCount + 1)
      else fail(s"HTTP message contains more than the configured limit of ${settings.maxHeaderCount} headers")
    } catch {
      case NotEnoughDataException ⇒ continueWith(() ⇒ headerLinesAux(messageLine, headers, headerCount), lineStart)
    }
  }

  def headerLinesAux(messageLine: ML, headers: List[HttpHeader], headerCount: Int) =
    headerLines(messageLine, headers, headerCount)

  def body(messageLine: ML, headers: List[HttpHeader]): ParsingResult = ParsingResult.NeedMoreData

  def messageChunk(): ParsingResult = ParsingResult.NeedMoreData

  def messagePart(messageLine: ML, headers: List[HttpHeader], contentType: Option[ContentType], body: Array[Byte]): T

  def prepareResult(part: T): ParsingResult = {
    val result =
      if (cursor == input.length) ParsingResult.Ok(part)
      else ParsingResult.OkAndContinue(part, input.drop(cursor))
    input = ByteString.empty
    run = this
    chunkExpected = !part.isInstanceOf[HttpMessageEnd]
    result
  }

  def continueWith(rule: () ⇒ ParsingResult, resetCursorTo: Int = 0): ParsingResult = {
    cursor = resetCursorTo
    run = { data ⇒
      if (data.nonEmpty) {
        input ++= data
        rule()
      } else ParsingResult.NeedMoreData
    }
    ParsingResult.NeedMoreData
  }

  def nextChar(): Char =
    if (cursor < input.length) {
      val char = input(cursor).toChar
      cursor += 1
      char
    } else throw NotEnoughDataException

  def crlf(errorStatus: StatusCode): Unit = if (nextChar() != '\r' || nextChar() != '\n') fail(errorStatus)

  def fail(summary: String): Nothing = fail(summary, "")
  def fail(summary: String, detail: String): Nothing = fail(StatusCodes.BadRequest, summary, detail)
  def fail(status: StatusCode): Nothing = fail(status, status.defaultMessage)
  def fail(status: StatusCode, summary: String, detail: String = ""): Nothing = fail(status, ErrorInfo(summary, detail))
  def fail(status: StatusCode, info: ErrorInfo): Nothing = {
    val e = new ParsingException(status, info)
    run = { _ ⇒ throw e }
    throw e
  }

  def charSequenceAdapter(start: Int, end: Int): CharSequence =
    new CharSequence {
      def charAt(index: Int) = input(index - start).toChar
      val length = end - start
      def subSequence(start: Int, end: Int) = charSequenceAdapter(start, end)
      override def toString = new String(input.iterator.slice(start, end).toArray, spray.util.UTF8)
    }

  def charSequenceAdapter(start: Int): CharSequence =
    new CharSequence {
      def charAt(index: Int) = {
        val ix = index - start
        if (ix < input.length) input(ix).toChar
        else throw NotEnoughDataException
      }
      def length = throw new UnsupportedOperationException
      def subSequence(start: Int, end: Int) = charSequenceAdapter(start, end)
    }
}

private[parsing] class HttpRequestPartParser(_settings: ParserSettings, _headerParser: HttpHeaderParser)
    extends AbstractHttpMessagePartParser[HttpRequestPart](_settings, _headerParser) {
  import HttpMessagePartParser._
  type ML = RequestLine

  override def messageLine(): ParsingResult =
    try {
      val meth = method()
      val uri = requestTarget()
      val proto = protocol()
      crlf(HTTPVersionNotSupported)
      headerLines(RequestLine(meth, uri, proto))
    } catch {
      case NotEnoughDataException ⇒ continueWith(messageLine)
    }

  def method(): HttpMethod = {
    def badMethod = fail(NotImplemented)
    @tailrec def method(meth: HttpMethod, ix: Int = 1): HttpMethod =
      if (ix == meth.value.length)
        if (nextChar() == ' ') meth else badMethod
      else if (nextChar() == meth.value.charAt(ix)) method(meth, ix + 1)
      else badMethod

    nextChar() match {
      case 'G' ⇒ method(GET)
      case 'P' ⇒ nextChar() match {
        case 'O' ⇒ method(POST, 2)
        case 'U' ⇒ method(PUT, 2)
        case 'A' ⇒ method(PATCH, 2)
        case _   ⇒ badMethod
      }
      case 'D' ⇒ method(DELETE)
      case 'H' ⇒ method(HEAD)
      case 'O' ⇒ method(OPTIONS)
      case 'T' ⇒ method(TRACE)
      case _   ⇒ badMethod
    }
  }

  def requestTarget(): Uri = {
    val uriStart = cursor
    val uriEndLimit = cursor + settings.maxUriLength

    @tailrec def findUriEnd(ix: Int = cursor, limit: Int = input.length): Int =
      if (ix == limit) throw NotEnoughDataException
      else if (input(ix) == ' ') ix
      else if (cursor < uriEndLimit) findUriEnd(ix + 1, limit)
      else fail(s"URI length exceeds the configured limit of ${settings.maxUriLength} characters")

    val uriEnd = findUriEnd()
    cursor = uriEnd + 1
    try Uri.parseHttpRequestTarget(charSequenceAdapter(uriStart, uriEnd))
    catch {
      case e: IllegalUriException ⇒ fail(BadRequest, e.info.withSummaryPrepended("Illegal request URI"))
    }
  }

  def protocol(): HttpProtocol =
    if (nextChar() == 'H' && nextChar() == 'T' && nextChar() == 'T' && nextChar() == 'P' && nextChar() == '/' &&
      nextChar() == '1' && nextChar() == '.') nextChar() match {
      case '0' ⇒ HttpProtocols.`HTTP/1.0`
      case '1' ⇒ HttpProtocols.`HTTP/1.1`
      case _   ⇒ fail(HTTPVersionNotSupported)
    }
    else fail(HTTPVersionNotSupported)

  def messagePart(requestLine: RequestLine, headers: List[HttpHeader], contentType: Option[ContentType],
                  body: Array[Byte]) =
    HttpRequest(
      method = requestLine.method,
      uri = requestLine.uri,
      headers = headers,
      entity = if (contentType.isEmpty) HttpEntity(body) else HttpBody(contentType.get, body),
      protocol = requestLine.protocol)
}