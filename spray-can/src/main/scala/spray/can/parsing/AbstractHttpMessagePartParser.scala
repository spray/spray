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
import akka.util.CompactByteString
import spray.http._
import HttpMethods._
import StatusCodes._
import HttpMessagePartParser._

private[parsing] abstract class HttpMessagePartParser(val settings: ParserSettings,
                                                      headerParser: HttpHeaderParser) extends Parser {
  type ML <: MessageLine

  var parse: Parse = _
  var protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`

  def parseProtocol(input: CompactByteString, cursor: Int): Int = {
    def c(ix: Int) = byteChar(input, cursor + ix)
    if (c(0) == 'H' && c(1) == 'T' && c(2) == 'T' && c(3) == 'P' && c(4) == '/' && c(5) == '1' && c(6) == '.') {
      protocol = c(6) match {
        case '0' ⇒ HttpProtocols.`HTTP/1.0`
        case '1' ⇒ HttpProtocols.`HTTP/1.1`
        case _   ⇒ fail(HTTPVersionNotSupported)
      }
      cursor + 7
    } else fail(HTTPVersionNotSupported)
  }

  @tailrec final def headerLines(messageLine: ML, input: CompactByteString, lineStart: Int,
                                 headers: List[HttpHeader] = Nil, headerCount: Int = 0): Result[Part] = {
    val lineEnd =
      try headerParser.parseHeaderLine(input, lineStart)()
      catch { case NotEnoughDataException ⇒ 0 }
    if (lineEnd > 0)
      if (headerParser.resultHeader eq HttpHeaderParser.EmptyHeader)
        body(messageLine, headers, input, lineEnd)
      else if (headerCount < settings.maxHeaderCount)
        headerLines(messageLine, input, lineEnd, headerParser.resultHeader :: headers, headerCount + 1)
      else fail(s"HTTP message contains more than the configured limit of ${settings.maxHeaderCount} headers")
    else continueWith(headerLines2(messageLine, _, lineStart, headers, headerCount), input)
  }

  private def headerLines2(messageLine: ML, input: CompactByteString, lineStart: Int, headers: List[HttpHeader],
                           headerCount: Int) = headerLines(messageLine, input, lineStart, headers, headerCount)

  private def body(messageLine: ML, headers: List[HttpHeader], input: CompactByteString, bodyStart: Int): Result[Part] = {
    messagePart(messageLine, headers, None, Array())
  }

  def messagePart(messageLine: ML, headers: List[HttpHeader], contentType: Option[ContentType],
                  body: Array[Byte]): Result[Part]

  def continueWith(rule: Parse, prependNewDataWith: CompactByteString): Result[Part] = {
    parse = { input ⇒ rule((prependNewDataWith ++ input).compact) }
    Result.NeedMoreData
  }

  def fail(summary: String): Nothing = fail(summary, "")
  def fail(summary: String, detail: String): Nothing = fail(StatusCodes.BadRequest, summary, detail)
  def fail(status: StatusCode): Nothing = fail(status, status.defaultMessage)
  def fail(status: StatusCode, summary: String, detail: String = ""): Nothing = fail(status, ErrorInfo(summary, detail))
  def fail(status: StatusCode, info: ErrorInfo): Nothing = {
    val e = new ParsingException(status, info)
    parse = { _ ⇒ throw e }
    throw e
  }
}

class HttpRequestPartParser(_settings: ParserSettings, _headerParser: HttpHeaderParser)
    extends HttpMessagePartParser(_settings, _headerParser) {
  type ML = RequestLine
  type Part = HttpRequestPart

  private[this] var method: HttpMethod = GET
  private[this] var uri: Uri = Uri.Empty

  parse = parseRequest

  def parseRequest(input: CompactByteString): Result[Part] =
    try {
      var cursor = parseMethod(input)
      cursor = requestTarget(input, cursor)
      cursor = parseProtocol(input, cursor)
      if (byteChar(input, cursor) != '\r' || byteChar(input, cursor + 1) != '\n') fail(HTTPVersionNotSupported)
      headerLines(RequestLine(method, uri, protocol), input, cursor + 2)
    } catch {
      case NotEnoughDataException ⇒ continueWith(parseRequest, input)
    }

  def parseMethod(input: CompactByteString): Int = {
    def badMethod = fail(NotImplemented)
    @tailrec def parseMethod(meth: HttpMethod, ix: Int = 1): Int =
      if (ix == meth.value.length)
        if (byteChar(input, ix) == ' ') {
          method = meth
          ix
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

  def requestTarget(input: CompactByteString, cursor: Int): Int = {
    val uriStart = cursor
    val uriEndLimit = cursor + settings.maxUriLength

    @tailrec def findUriEnd(ix: Int = cursor, limit: Int = input.length): Int =
      if (ix == limit) throw NotEnoughDataException
      else if (input(ix) == ' ') ix
      else if (cursor < uriEndLimit) findUriEnd(ix + 1, limit)
      else fail(s"URI length exceeds the configured limit of ${settings.maxUriLength} characters")

    val uriEnd = findUriEnd()
    try uri = Uri.parseHttpRequestTarget(input.iterator.drop(uriStart).take(uriEnd - uriStart).toArray[Byte])
    catch {
      case e: IllegalUriException ⇒ fail(BadRequest, e.info.withSummaryPrepended("Illegal request URI"))
    }
    uriEnd
  }

  def messagePart(requestLine: RequestLine, headers: List[HttpHeader], contentType: Option[ContentType],
                  body: Array[Byte]): Result[Part] =
    Result.Ok {
      HttpRequest(
        method = requestLine.method,
        uri = requestLine.uri,
        headers = headers,
        entity = if (contentType.isEmpty) HttpEntity(body) else HttpBody(contentType.get, body),
        protocol = requestLine.protocol)
    }
}

object HttpMessagePartParser {
  sealed trait Parser {
    type Part <: HttpMessagePart
    type Parse = CompactByteString ⇒ Result[Part]
    def parse: Parse
  }

  sealed trait Result[+T <: HttpMessagePart]
  object Result {
    case object NeedMoreData extends Result[Nothing]
    case class Ok[T <: HttpMessagePart](part: T) extends Result[T]
    case class OkAndContinue[T <: HttpMessagePart](part: T, remainingData: CompactByteString) extends Result[T]
  }

  sealed trait MessageLine
  case class RequestLine(method: HttpMethod, uri: Uri, protocol: HttpProtocol) extends MessageLine
  case class StatusLine(protocol: HttpProtocol,
                        status: Int,
                        reason: String,
                        isResponseToHeadRequest: Boolean = false) extends MessageLine
}