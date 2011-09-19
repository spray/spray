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

// a MessageParser instance holds the complete parsing state at any particular point in the request parsing process
private[can] trait MessageParser

private[can] sealed trait IntermediateParser extends MessageParser {
  def read(buf: ByteBuffer): MessageParser
}

private[can] sealed abstract class CharacterParser extends IntermediateParser {
  def read(buf: ByteBuffer): MessageParser = {
    @tailrec
    def read(parser: MessageParser): MessageParser = parser match {
      case x: CharacterParser => {
        if (buf.remaining() > 0) {
          val cursor = buf.get().asInstanceOf[Char] // simple US-ASCII encoding conversion
          read(x.handleChar(cursor))
        } else x
      }
      case x: IntermediateParser => x.read(buf) // a body parser
      case x => x // complete or error
    }
    read(this)
  }

  def handleChar(cursor: Char): MessageParser

  def isTokenChar(c: Char) = c match {
    case x if 'a' <= x && x <= 'z' => true
    case x if 'A' <= x && x <= 'Z' => true
    case '-' => true
    case '(' | ')' | '<' | '>' | '@' | ',' | ';' | ':' | '\\' | '"' | '/' | '[' | ']' | '?' | '=' | '{' | '}' => false
    case x => 32 < x && x < 127
  }

  def badMethod = MessageError("Unsupported HTTP method", 501)
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
      if (cursor == current) this
      else badMethod
    } else {
      if (cursor == ' ') new UriParser(config, method)
      else badMethod
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
    } else MessageError("URIs with more than " + config.maxUriLength + " characters are not supported", 414)
  }
}

private[can] abstract class VersionParser extends CharacterParser {
  import HttpProtocols._
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
  def badVersion = MessageError("HTTP Version not supported", 505)
}

private[can] class RequestVersionParser(config: MessageParserConfig, method: HttpMethod, uri: String)
        extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == '\r') { pos = 9; this } else badVersion
    case 9 => if (cursor == '\n') new HeaderNameParser(config, RequestLine(method, uri, protocol)) else badVersion
  }
}

private[can] class EmptyResponseParser(config: MessageParserConfig, request: HttpRequest) extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == ' ') new StatusCodeParser(config, request, protocol) else badVersion
  }
}

private[can] class StatusCodeParser(config: MessageParserConfig, request: HttpRequest, protocol: HttpProtocol)
        extends CharacterParser {
  var pos = 0
  var status = 0
  def handleChar(cursor: Char) = pos match {
    case 0 => if ('1' <= cursor && cursor <= '5') { pos = 1; status = (cursor - '0') * 100; this } else badStatus
    case 1 => if ('0' <= cursor && cursor <= '9') { pos = 2; status += (cursor - '0') * 10; this } else badStatus
    case 2 => if ('0' <= cursor && cursor <= '9') { pos = 3; status += cursor - '0'; this } else badStatus
    case 3 => if (cursor == ' ') new ReasonParser(config, request, protocol, status) else badStatus
  }
  def badStatus = MessageError("Illegal response status code")
}

private[can] class ReasonParser(config: MessageParserConfig, request: HttpRequest, protocol: HttpProtocol, status: Int)
        extends CharacterParser {
  val reason = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (reason.length <= config.maxResponseReasonLength) {
      cursor match {
        case '\r' => this
        case '\n' => new HeaderNameParser(config, StatusLine(request, protocol, status, reason.toString))
        case _ => reason.append(cursor); this
      }
    } else MessageError("Reason phrases with more than " + config.maxResponseReasonLength + " characters are not supported")
  }
}

private[can] class HeaderNameParser(config: MessageParserConfig, messageLine: MessageLine,
                                    headerCount: Int = 0, headers: List[HttpHeader] = Nil)
        extends CharacterParser {
  val headerName = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (headerName.length <= config.maxHeaderNameLength) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(x); this
        case ':' => new LwsParser(new HeaderValueParser(config, messageLine, headerCount, headers, headerName.toString))
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete(headers, headers, None, None, None)
        case ' ' | '\t' | '\r' => new LwsParser(this).handleChar(cursor)
        case _ => MessageError("Invalid character '" + cursor + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else MessageError("HTTP headers with names longer than " + config.maxHeaderNameLength + " characters are not supported")
  }
  @tailrec
  private def headersComplete(remaining: List[HttpHeader], headers: List[HttpHeader], cHeader: Option[String],
                              clHeader: Option[String], teHeader: Option[String]): MessageParser = {
    def messageBodyDisallowed = messageLine match {
      case _: RequestLine => false // there can always be a body in a request
      case StatusLine(request, _, status, _) => // certain responses never have a body
        (status / 100 == 1) || status == 204 || status == 304 || request.method == HttpMethods.HEAD
    }
    if (!remaining.isEmpty) remaining.head.name match {
      case "Content-Length" =>
        if (clHeader.isEmpty) headersComplete(remaining.tail, headers, cHeader, Some(remaining.head.value), teHeader)
        else MessageError("HTTP message must not contain more than one Content-Length header", 400)
      case "Transfer-Encoding" => headersComplete(remaining.tail, headers, cHeader, clHeader, Some(remaining.head.value))
      case "Connection" => headersComplete(remaining.tail, headers, Some(remaining.head.value), clHeader, teHeader)
      case _ => headersComplete(remaining.tail, headers, cHeader, clHeader, teHeader)
    } else {
      // rfc2616 sec. 4.4
      if (messageBodyDisallowed)
        CompleteMessage(messageLine, headers, cHeader)
      else if (teHeader.isDefined && teHeader.get != "identity")
        MessageError("Non-identity transfer encodings are not currently supported", 501)
      else if (clHeader.isDefined) clHeader.get match {
        case "0" => CompleteMessage(messageLine, headers, cHeader)
        case value => try { new FixedLengthBodyParser(config, messageLine, headers, cHeader, value.toInt) }
                      catch { case e: Exception => MessageError("Invalid Content-Length header value: " + e.getMessage) }
      } else messageLine match {
        case RequestLine(_, _, HttpProtocols.`HTTP/1.0`) => CompleteMessage(messageLine, headers, cHeader)
        case _: StatusLine => new ToCloseBodyParser(config, messageLine, headers, cHeader)
        case _ => MessageError("Content-Length header or chunked transfer encoding required", 411)
      }
    }
  }
}

private[can] class HeaderValueParser(config: MessageParserConfig, messageLine: MessageLine, headerCount: Int,
                                     headers: List[HttpHeader], headerName: String)
        extends CharacterParser {
  val headerValue = new JStringBuilder
  var space = false
  def handleChar(cursor: Char) = {
    if (headerValue.length <= config.maxHeaderValueLength) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new LwsParser(this).handleChar(cursor)
        case '\n' =>
          if (headerCount < config.maxHeaderCount)
            new HeaderNameParser(config, messageLine, headerCount + 1, HttpHeader(headerName, headerValue.toString) :: headers)
          else
            MessageError("HTTP message with more than " + config.maxHeaderCount + " headers are not supported", 400)
        case _ =>
          if (space) { headerValue.append(' '); space = false }
          headerValue.append(cursor)
          this
      }
    } else MessageError("HTTP header values longer than " + config.maxHeaderValueLength +
            " characters are not supported (header '" + headerName + "')")
  }
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
  require(totalBytes <= config.maxBodyLength, "HTTP message body size exceeds configured limit")

  val body = new Array[Byte](totalBytes)
  var bytesRead = 0
  def read(buf: ByteBuffer) = {
    val remaining = scala.math.min(buf.remaining, totalBytes - bytesRead)
    buf.get(body, bytesRead, remaining)
    bytesRead += remaining
    if (bytesRead == totalBytes) new CompleteMessage(messageLine, headers, connectionHeader, body) else this
  }
}

private[can] class ToCloseBodyParser(config: MessageParserConfig, messageLine: MessageLine, headers: List[HttpHeader],
                                     connectionHeader: Option[String]) extends IntermediateParser {
  private var body: Array[Byte] = EmptyByteArray
  def read(buf: ByteBuffer) = {
    val array = new Array[Byte](buf.remaining)
    buf.get(array)
    body match {
      case EmptyByteArray =>
        body = array
        this
      case _ => {
        val newLength = body.length + array.length
        if (newLength <= config.maxBodyLength) {
          body = make(new Array[Byte](newLength)) { newBody =>
            System.arraycopy(body, 0, newBody, 0, body.length)
            System.arraycopy(array, 0, newBody, body.length, array.length)
          }
          this
        } else MessageError("HTTP message body size exceeds configured limit")
      }
    }
  }
  def complete = new CompleteMessage(messageLine, headers, connectionHeader, body)
}

private[can] case class CompleteMessage(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  body: Array[Byte] = EmptyByteArray
) extends MessageParser

private[can] case class MessageError(message: String, status: Int = 400) extends MessageParser