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
      case x: IntermediateParser => x.read(buf) // InBodyMessageParser
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

  def badMethod = new ErrorMessageParser("Unsupported HTTP method", 501)
}

private[can] object EmptyRequestParser extends CharacterParser {
  import HttpMethods._
  def handleChar(cursor: Char) = cursor match {
    case 'G' => new InMethodRequestParser(GET)
    case 'P' => new CharacterParser {
      override def handleChar(cursor: Char) = cursor match {
        case 'O' => new InMethodRequestParser(POST, 1)
        case 'U' => new InMethodRequestParser(PUT, 1)
        case _ => badMethod
      }
    }
    case 'D' => new InMethodRequestParser(DELETE)
    case 'H' => new InMethodRequestParser(HEAD)
    case 'O' => new InMethodRequestParser(OPTIONS)
    case 'T' => new InMethodRequestParser(TRACE)
    case 'C' => new InMethodRequestParser(CONNECT)
    case _ => badMethod
  }
}

private[can] class InMethodRequestParser(method: HttpMethod, var pos: Int = 0) extends CharacterParser {
  def handleChar(cursor: Char) = {
    pos += 1
    if (pos < method.name.length()) {
      val current = method.name.charAt(pos)
      if (cursor == current) this
      else badMethod
    } else {
      if (cursor == ' ') new InUriRequestParser(method)
      else badMethod
    }
  }
}

private[can] class InUriRequestParser(method: HttpMethod) extends CharacterParser {
  val uri = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (uri.length <= 2048) {
      cursor match {
        case ' ' => new InVersionRequestParser(method, uri.toString)
        case _ => uri.append(cursor); this
      }
    } else new ErrorMessageParser("URIs with more than 2048 characters are not supported", 414)
  }
}

private[can] abstract class InVersionMessageParser extends CharacterParser {
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
  def badVersion = new ErrorMessageParser("HTTP Version not supported", 505)
}

private[can] class InVersionRequestParser(method: HttpMethod, uri: String) extends InVersionMessageParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == '\r') { pos = 9; this } else badVersion
    case 9 => if (cursor == '\n') new InHeaderNameMessageParser(RequestLine(method, uri, protocol)) else badVersion
  }
}

private[can] class EmptyResponseParser extends InVersionMessageParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == ' ') new InStatusCodeResponseParser(protocol) else badVersion
  }
}

private[can] class InStatusCodeResponseParser(protocol: HttpProtocol) extends CharacterParser {
  var pos = 0
  var status = 0
  def handleChar(cursor: Char) = pos match {
    case 0 => if ('1' <= cursor && cursor <= '5') { pos = 1; status = (cursor - '0') * 100; this } else badStatus
    case 1 => if ('0' <= cursor && cursor <= '9') { pos = 2; status += (cursor - '0') * 10; this } else badStatus
    case 2 => if ('0' <= cursor && cursor <= '9') { pos = 3; status += cursor - '0'; this } else badStatus
    case 3 => if (cursor == ' ') new InReasonResponseParser(protocol, status) else badStatus
  }
  def badStatus = new ErrorMessageParser("Illegal response status code")
}

private[can] class InReasonResponseParser(protocol: HttpProtocol, status: Int) extends CharacterParser {
  val reason = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (reason.length <= 64) {
      cursor match {
        case '\r' => this
        case '\n' => new InHeaderNameMessageParser(StatusLine(protocol, status, reason.toString))
        case _ => reason.append(cursor); this
      }
    } else new ErrorMessageParser("Reason phrases with more than 64 characters are not supported")
  }
}

private[can] class InHeaderNameMessageParser(messageLine: MessageLine, headers: List[HttpHeader] = Nil)
        extends CharacterParser {
  val headerName = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (headerName.length <= 64) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(x); this
        case ':' => new InLwsMessageParser(new InHeaderValueMessageParser(messageLine, headers, headerName.toString))
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete
        case ' ' | '\t' | '\r' => new InLwsMessageParser(this).handleChar(cursor)
        case _ => new ErrorMessageParser("Invalid character '" + cursor + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else new ErrorMessageParser("HTTP headers with names longer than 64 characters are not supported")
  }
  def headersComplete = {
    var transferEncodingHeader: Option[String] = None
    var contentLengthHeader: Option[String] = None
    var connectionHeader: Option[String] = None
    headers.foreach { h =>
      h.name match {
        case "Content-Length" if h.value != "0" => contentLengthHeader = Some(h.value)
        case "Transfer-Encoding" if h.value != "identity" => transferEncodingHeader = Some(h.value)
        case "Connection" => connectionHeader = Some(h.value)
        case _ =>
      }
    }
    if (transferEncodingHeader.isEmpty) {
      contentLengthHeader match {
        case Some(length) =>
          try { new InBodyMessageParser(messageLine, headers, connectionHeader, length.toInt) }
          catch { case _: Exception => new ErrorMessageParser("Invalid Content-Length header value") }
        case None => CompleteMessageParser(messageLine, headers, connectionHeader)
      }
    } else new ErrorMessageParser("Non-identity transfer encodings are not currently supported", 501)
  }
}

private[can] class InHeaderValueMessageParser(messageLine: MessageLine, headers: List[HttpHeader], headerName: String)
        extends CharacterParser {
  val headerValue = new JStringBuilder
  var space = false
  def handleChar(cursor: Char) = {
    if (headerValue.length <= 8192) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new InLwsMessageParser(this).handleChar(cursor)
        case '\n' => new InHeaderNameMessageParser(messageLine, HttpHeader(headerName, headerValue.toString) :: headers)
        case _ =>
          if (space) { headerValue.append(' '); space = false }
          headerValue.append(cursor)
          this
      }
    } else new ErrorMessageParser("HTTP header values longer than 8192 characters are not supported (" +
            "header '" + headerName + "')")
  }
}

private[can] class InLwsMessageParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case ' ' | '\t' => this
      case '\r' => new InLwsCrLfMessageParser(next)
      case x => next.handleChar(x)
    }
  }
}

private[can] class InLwsCrLfMessageParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case '\n' => this
      case ' ' | '\t' => new InLwsMessageParser(next)
      case x => {
        // we encountered a real CRLF without following whitespace,
        // so we need to handle the newline before the current cursor
        next.handleChar('\n').asInstanceOf[CharacterParser].handleChar(x)
      }
    }
  }
}

private[can] class InBodyMessageParser(messageLine: MessageLine, headers: List[HttpHeader],
                                       connectionHeader: Option[String], totalBytes: Int) extends IntermediateParser {
  require(totalBytes >= 0, "Content-Length must not be negative")
  val body = new Array[Byte](totalBytes)
  var bytesRead = 0
  def read(buf: ByteBuffer) = {
    val remaining = scala.math.min(buf.remaining, totalBytes - bytesRead)
    buf.get(body, bytesRead, remaining)
    bytesRead += remaining
    if (bytesRead == totalBytes) new CompleteMessageParser(messageLine, headers, connectionHeader, body) else this
  }
}

private[can] case class CompleteMessageParser(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  body: Array[Byte] = EmptyByteArray
) extends MessageParser

private[can] case class ErrorMessageParser(message: String, status: Int = 400) extends MessageParser