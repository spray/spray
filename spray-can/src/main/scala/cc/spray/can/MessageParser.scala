/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package can

import java.nio.ByteBuffer
import java.lang.{StringBuilder => JStringBuilder}
import annotation.tailrec
import HttpProtocols._

// a MessageParser instance holds the complete parsing state at any particular point in the request or response
// parsing process
private[can] trait MessageParser

// a MessageParser holding an intermediate parsing state, i.e. which does not represent a complete parsing result
private[can] sealed trait IntermediateParser extends MessageParser {
  def read(buf: ByteBuffer): MessageParser
}

// an IntermediateParser working on US-ASCII encoded characters (e.g. the HTTP messages header section)
private[can] sealed abstract class CharacterParser extends IntermediateParser {
  def read(buf: ByteBuffer): MessageParser = {
    @tailrec
    def read(parser: MessageParser): MessageParser = parser match {
      case x: CharacterParser => {
        if (buf.remaining() > 0) {
          val cursor = buf.get().asInstanceOf[Char] // simple US-ASCII encoding conversion
          read(x.handleChar(cursor))
        } else {
          x
        }
      }
      case x: IntermediateParser => x.read(buf) // a body parser
      case x => x // complete or error
    }
    read(this)
  }

  def handleChar(cursor: Char): MessageParser

  def badMethod = ErrorParser("Unsupported HTTP method", 501)
}

private[can] class EmptyRequestParser(config: MessageParserConfig) extends CharacterParser {
  import HttpMethods._
  def handleChar(cursor: Char) = cursor match {
    case 'G' => new MethodParser(config, GET)
    case 'P' => new CharacterParser {
      override def handleChar(cursor: Char) = cursor match {
        case 'O' => new MethodParser(config, POST, 1)
        case 'U' => new MethodParser(config, PUT, 1)
        case _ => badMethod
      }
    }
    case 'D' => new MethodParser(config, DELETE)
    case 'H' => new MethodParser(config, HEAD)
    case 'O' => new MethodParser(config, OPTIONS)
    case 'T' => new MethodParser(config, TRACE)
    case 'C' => new MethodParser(config, CONNECT)
    case _ => badMethod
  }
}

private[can] class MethodParser(config: MessageParserConfig, method: HttpMethod, var pos: Int = 0)
        extends CharacterParser {
  def handleChar(cursor: Char) = {
    pos += 1
    if (pos < method.name.length()) {
      val current = method.name.charAt(pos)
      if (cursor == current) {
        this
      }
      else {
        badMethod
      }
    } else {
      if (cursor == ' ') {
        new UriParser(config, method)
      }
      else {
        badMethod
      }
    }
  }
}

private[can] class UriParser(config: MessageParserConfig, method: HttpMethod) extends CharacterParser {
  val uri = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (uri.length <= config.maxUriLength) {
      cursor match {
        case ' ' => new RequestVersionParser(config, method, uri.toString)
        case _ => uri.append(cursor); this
      }
    } else {
      ErrorParser("URIs with more than " + config.maxUriLength + " characters are not supported", 414)
    }
  }
}

private[can] abstract class VersionParser extends CharacterParser {
  var pos = 0
  var protocol: HttpProtocol = _
  def handleChar(cursor: Char) = pos match {
    case 0 => if (cursor == 'H') { pos = 1; this } else badVersion
    case 1 => if (cursor == 'T') { pos = 2; this } else badVersion
    case 2 => if (cursor == 'T') { pos = 3; this } else badVersion
    case 3 => if (cursor == 'P') { pos = 4; this } else badVersion
    case 4 => if (cursor == '/') { pos = 5; this } else badVersion
    case 5 => if (cursor == '1') { pos = 6; this } else badVersion
    case 6 => if (cursor == '.') { pos = 7; this } else badVersion
    case 7 => cursor match {
      case '1' => protocol = `HTTP/1.1`; pos = 8; this
      case '0' => protocol = `HTTP/1.0`; pos = 8; this
      case _ => badVersion
    }
    case _ => handleSuffix(cursor)
  }
  def handleSuffix(cursor: Char): MessageParser
  def badVersion = ErrorParser("HTTP Version not supported", 505)
}

private[can] class RequestVersionParser(config: MessageParserConfig, method: HttpMethod, uri: String)
        extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == '\r') { pos = 9; this } else badVersion
    case 9 => if (cursor == '\n') new HeaderNameParser(config, RequestLine(method, uri, protocol)) else badVersion
  }
}

private[can] class EmptyResponseParser(config: MessageParserConfig, requestMethod: HttpMethod) extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == ' ') new StatusCodeParser(config, requestMethod, protocol) else badVersion
  }
}

private[can] class StatusCodeParser(config: MessageParserConfig, requestMethod: HttpMethod, protocol: HttpProtocol)
        extends CharacterParser {
  var pos = 0
  var status = 0
  def handleChar(cursor: Char) = pos match {
    case 0 => if ('1' <= cursor && cursor <= '5') { pos = 1; status = (cursor - '0') * 100; this } else badStatus
    case 1 => if ('0' <= cursor && cursor <= '9') { pos = 2; status += (cursor - '0') * 10; this } else badStatus
    case 2 => if ('0' <= cursor && cursor <= '9') { pos = 3; status += cursor - '0'; this } else badStatus
    case 3 => if (cursor == ' ') new ReasonParser(config, requestMethod, protocol, status) else badStatus
  }
  def badStatus = ErrorParser("Illegal response status code")
}

private[can] class ReasonParser(config: MessageParserConfig, requestMethod: HttpMethod, protocol: HttpProtocol, status: Int)
        extends CharacterParser {
  val reason = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (reason.length <= config.maxResponseReasonLength) {
      cursor match {
        case '\r' => this
        case '\n' => new HeaderNameParser(config, StatusLine(requestMethod, protocol, status, reason.toString))
        case _ => reason.append(cursor); this
      }
    } else {
      ErrorParser("Reason phrases with more than " + config.maxResponseReasonLength + " characters are not supported")
    }
  }
}

private[can] class HeaderNameParser(config: MessageParserConfig, messageLine: MessageLine, headerCount: Int = 0,
                                    headers: List[HttpHeader] = Nil)
        extends CharacterParser {
  val headerName = new JStringBuilder
  def valueParser = new HeaderValueParser(config, messageLine, headerCount, headers, headerName.toString)
  def handleChar(cursor: Char) = {
    if (headerName.length <= config.maxHeaderNameLength) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(x); this
        case ':' => new LwsParser(valueParser)
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete
        case ' ' | '\t' | '\r' => new LwsParser(this).handleChar(cursor)
        case _ => ErrorParser("Invalid character '" + cursor + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else {
      ErrorParser("HTTP headers with names longer than " + config.maxHeaderNameLength + " characters are not supported")
    }
  }
  def headersComplete = {
    @tailrec def traverse(remaining: List[HttpHeader], connection: Option[String], contentLength: Option[String],
                          transferEncoding: Option[String], hostHeaderPresent: Boolean): MessageParser = {
      if (!remaining.isEmpty) {
        remaining.head.name match {
          case "Content-Length" =>
            if (contentLength.isEmpty) {
              traverse(remaining.tail, connection, Some(remaining.head.value), transferEncoding, hostHeaderPresent)
            } else ErrorParser("HTTP message must not contain more than one Content-Length header", 400)
          case "Transfer-Encoding" => traverse(remaining.tail, connection, contentLength, Some(remaining.head.value), hostHeaderPresent)
          case "Connection" => traverse(remaining.tail, Some(remaining.head.value), contentLength, transferEncoding, hostHeaderPresent)
          case "Host" =>
            if (!hostHeaderPresent) traverse(remaining.tail, connection, contentLength, transferEncoding, true)
            else ErrorParser("HTTP message must not contain more than one Host header", 400)
          case _ => traverse(remaining.tail, connection, contentLength, transferEncoding, hostHeaderPresent)
        }
      } else messageLine match { // rfc2616 sec. 4.4
        case x: RequestLine if x.protocol == `HTTP/1.1` && !hostHeaderPresent =>
          ErrorParser("Host header required", 400)
        case _ if messageBodyDisallowed =>
          CompleteMessageParser(messageLine, headers, connection)
        case _ if transferEncoding.isDefined && transferEncoding.get != "identity" =>
          ChunkedStartParser(messageLine, headers, connection)
        case _ if contentLength.isDefined =>
          contentLength.get match {
            case "0" => CompleteMessageParser(messageLine, headers, connection)
            case value => try {new FixedLengthBodyParser(config, messageLine, headers, connection, value.toInt)}
            catch {case e: Exception => ErrorParser("Invalid Content-Length header value: " + e.getMessage)}
          }
        case _: RequestLine => CompleteMessageParser(messageLine, headers, connection)
        case x: StatusLine if connection == Some("close") || connection.isEmpty && x.protocol == `HTTP/1.0` =>
          new ToCloseBodyParser(config, messageLine, headers, connection)
        case _ => ErrorParser("Content-Length header or chunked transfer encoding required", 411)
      }
    }
    traverse(headers, None, None, None, false)
  }
  def messageBodyDisallowed = messageLine match {
    case _: RequestLine => false // there can always be a body in a request
    case StatusLine(requestMethod, _, status, _) => // certain responses never have a body
      (status / 100 == 1) || status == 204 || status == 304 || requestMethod == HttpMethods.HEAD
  }
}

private[can] class HeaderValueParser(config: MessageParserConfig, messageLine: MessageLine, headerCount: Int,
                                     headers: List[HttpHeader], val headerName: String)
        extends CharacterParser {
  val headerValue = new JStringBuilder
  var space = false
  def nameParser =
    new HeaderNameParser(config, messageLine, headerCount + 1, HttpHeader(headerName, headerValue.toString) :: headers)
  def handleChar(cursor: Char) = {
    if (headerValue.length <= config.maxHeaderValueLength) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new LwsParser(this).handleChar(cursor)
        case '\n' => if (headerCount < config.maxHeaderCount) {
          nameParser
        } else {
          ErrorParser("HTTP message with more than " + config.maxHeaderCount + " headers are not supported", 400)
        }
        case _ =>
          if (space) {headerValue.append(' '); space = false}
          headerValue.append(cursor)
          this
      }
    } else {
      ErrorParser("HTTP header values longer than " + config.maxHeaderValueLength +
              " characters are not supported (header '" + headerName + "')")
    }
  }
}

private[can] class ChunkParser(config: MessageParserConfig) extends CharacterParser {
  var chunkSize = -1
  def handle(digit: Int) = {
    chunkSize = if (chunkSize == -1) digit else chunkSize * 16 + digit
    if (chunkSize > config.maxChunkSize) ErrorParser("HTTP message chunk size exceeds configured limit") else this
  }
  def handleChar(cursor: Char) = cursor match {
    case x if '0' <= cursor && cursor <= '9' => handle(x - '0')
    case x if 'A' <= cursor && cursor <= 'F' => handle(x - 'A' + 10)
    case x if 'a' <= cursor && cursor <= 'f' => handle(x - 'a' + 10)
    case ' ' | '\t' | '\r' => this
    case '\n' => chunkSize match {
      case -1 => ErrorParser("Chunk size expected")
      case 0 => new TrailerParser(config)
      case _ => new ChunkBodyParser(config, chunkSize)
    }
    case ';'  => new ChunkExtensionNameParser(config, chunkSize)
    case _ => ErrorParser("Illegal chunk size")
  }
}

private[can] class ChunkExtensionNameParser(config: MessageParserConfig, chunkSize: Int, extCount: Int = 0,
                                            extensions: List[ChunkExtension] = Nil)
        extends CharacterParser {
  val extName = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (extName.length <= config.maxChunkExtNameLength) {
      cursor match {
        case x if isTokenChar(x) => extName.append(x); this
        case '=' => new ChunkExtensionValueParser(config, chunkSize, extCount, extensions, extName.toString)
        case ' ' | '\t' => this
        case _ => ErrorParser("Invalid character '" + cursor + "', expected TOKEN CHAR, SPACE, TAB or EQUAL")
      }
    } else {
      ErrorParser("Chunk extensions with names longer than " + config.maxChunkExtNameLength +
              " characters are not supported")
    }
  }
}

private[can] class ChunkExtensionValueParser(config: MessageParserConfig, chunkSize: Int, extCount: Int,
                                             extensions: List[ChunkExtension], extName: String)
        extends CharacterParser {
  val extValue = new JStringBuilder
  var quoted = false
  def next(parser: MessageParser) = if (extCount < config.maxChunkExtCount) {
    parser
  } else {
    ErrorParser("Chunks with more than " + config.maxChunkExtCount + " extensions are not supported", 400)
  }
  def newExtensions = ChunkExtension(extName, extValue.toString) :: extensions
  def handleChar(cursor: Char) = {
    if (extValue.length <= config.maxChunkExtValueLength) {
      if (quoted) {
        cursor match {
          case '"' => quoted = false; this
          case '\r' | '\n' => ErrorParser("Invalid chunk extension value: unclosed quoted string")
          case x => extValue.append(x); this
        }
      } else {
        cursor match {
          case x if isTokenChar(x) => extValue.append(x); this
          case '"' if extValue.length == 0 => quoted = true; this
          case ' ' | '\t' | '\r' => this
          case ';' => next(new ChunkExtensionNameParser(config, chunkSize, extCount + 1, newExtensions))
          case '\n' => next {
            if (chunkSize == 0) new TrailerParser(config, newExtensions)
            else new ChunkBodyParser(config, chunkSize, newExtensions)
          }
          case _ => ErrorParser("Invalid character '" + cursor + "', expected TOKEN CHAR, SPACE, TAB or EQUAL")
        }
      }
    } else {
      ErrorParser("Chunk extensions with values longer than " + config.maxChunkExtValueLength +
              " characters are not supported (extension '" + extName + "')")
    }
  }
}

private[can] class TrailerParser(config: MessageParserConfig,
                                 extensions: List[ChunkExtension] = Nil, headerCount: Int = 0,
                                 headers: List[HttpHeader] = Nil)
        extends HeaderNameParser(config, null, headerCount, headers) {
  override def valueParser = new HeaderValueParser(config, null, headerCount, headers, headerName.toString) {
    override def nameParser =
      new TrailerParser(config, extensions, headerCount + 1, HttpHeader(headerName, headerValue.toString) :: headers)
  }
  override def headersComplete = ChunkedEndParser(extensions, headers)
}

private[can] class LwsParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case ' ' | '\t' => this
      case '\r' => new LwsCrLfParser(next)
      case x => next.handleChar(x)
    }
  }
}

private[can] class LwsCrLfParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case '\n' => this
      case ' ' | '\t' => new LwsParser(next)
      case x => {
        // we encountered a real CRLF without following whitespace,
        // so we need to handle the newline before the current cursor
        next.handleChar('\n').asInstanceOf[CharacterParser].handleChar(x)
      }
    }
  }
}

private[can] class FixedLengthBodyParser(config: MessageParserConfig, messageLine: MessageLine,
                                         headers: List[HttpHeader], connectionHeader: Option[String], totalBytes: Int)
        extends IntermediateParser {
  require(totalBytes >= 0, "Content-Length must not be negative")
  require(totalBytes <= config.maxContentLength, "HTTP message Content-Length " + totalBytes + " exceeds configured limit")

  val body = new Array[Byte](totalBytes)
  var bytesRead = 0
  def read(buf: ByteBuffer) = {
    val remaining = scala.math.min(buf.remaining, totalBytes - bytesRead)
    buf.get(body, bytesRead, remaining)
    bytesRead += remaining
    if (bytesRead == totalBytes) CompleteMessageParser(messageLine, headers, connectionHeader, body) else this
  }
}

private[can] class ToCloseBodyParser(config: MessageParserConfig, messageLine: MessageLine, headers: List[HttpHeader],
                                     connectionHeader: Option[String]) extends IntermediateParser {
  private var body: Array[Byte] = EmptyByteArray
  def read(buf: ByteBuffer) = {
    val array = new Array[Byte](buf.remaining)
    buf.get(array)
    body match {
      case EmptyByteArray => body = array; this
      case _ => {
        if (body.length + array.length <= config.maxContentLength) {
          body = body concat array
          this
        } else ErrorParser("HTTP message body size exceeds configured limit", 413)
      }
    }
  }
  def complete = CompleteMessageParser(messageLine, headers, connectionHeader, body)
}

private[can] class ChunkBodyParser(config: MessageParserConfig, chunkSize: Int,
                                   extensions: List[ChunkExtension] = Nil) extends IntermediateParser {
  require(chunkSize > 0, "Chunk size must not be negative")
  require(chunkSize <= config.maxChunkSize, "HTTP message chunk size " + chunkSize + " exceeds configured limit")

  val body = new Array[Byte](chunkSize)
  var bytesRead = 0

  @tailrec
  final def read(buf: ByteBuffer) = {
    if (bytesRead < chunkSize) {
      val remaining = scala.math.min(buf.remaining, chunkSize - bytesRead)
      buf.get(body, bytesRead, remaining)
      bytesRead += remaining
      if (buf.remaining == 0) this else read(buf)
    } else if (bytesRead == chunkSize) {
      if (buf.get() == '\r'.asInstanceOf[Byte]) {
        bytesRead += 1
        if (buf.remaining == 0) this else read(buf)
      } else ErrorParser("Expected CR after message chunk")
    } else {
      if (buf.get() == '\n'.asInstanceOf[Byte]) ChunkedChunkParser(extensions, body)
      else ErrorParser("Expected LF after CR after message chunk")
    }
  }
}

private[can] case class CompleteMessageParser(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  body: Array[Byte] = EmptyByteArray
) extends MessageParser

private[can] case class ChunkedStartParser(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None
) extends MessageParser

private[can] case class ChunkedChunkParser(
  extensions: List[ChunkExtension],
  body: Array[Byte]
) extends MessageParser

private[can] case class ChunkedEndParser(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader]
) extends MessageParser

private[can] class ErrorParser(val message: String, val status: Int) extends MessageParser {
  override def hashCode = message.## * 31 + status
  override def equals(obj: Any) = obj match {
    case x: ErrorParser => x.message == message && x.status == status
    case _ => false
  }
  override def toString = "ErrorParser(" + message + ", " + status + ")"
}

private[can] object ErrorParser {
  def apply(message: String, status: Int = 400): ErrorParser = new ErrorParser(
    message.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"),
    status
  )
}

