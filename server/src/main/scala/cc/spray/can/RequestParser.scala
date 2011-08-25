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

// a RequestParser instance holds the complete parsing state at any particular point in the request parsing process
sealed trait RequestParser

trait IntermediateParser extends RequestParser {
  def read(buf: ByteBuffer): RequestParser
}

abstract class CharacterParser extends IntermediateParser {
  def read(buf: ByteBuffer): RequestParser = {
    @tailrec
    def read(parser: RequestParser): RequestParser = parser match {
      case x: CharacterParser => {
        if (buf.remaining() > 0) {
          val cursor = buf.get().asInstanceOf[Char] // simple US-ASCII encoding conversion
          read(x.handleChar(cursor))
        } else x
      }
      case x: IntermediateParser => x.read(buf) // InBodyRequestParser
      case x => x // complete or error
    }
    read(this)
  }

  def handleChar(cursor: Char): RequestParser

  def isTokenChar(c: Char) = c match {
    case x if 'a' <= x && x <= 'z' => true
    case x if 'A' <= x && x <= 'Z' => true
    case '-' => true
    case '(' | ')' | '<' | '>' | '@' | ',' | ';' | ':' | '\\' | '"' | '/' | '[' | ']' | '?' | '=' | '{' | '}' => false
    case x => 32 < x && x < 127
  }

  def badMethod = new ErrorRequestParser(501, "Unsupported HTTP method")
}

object EmptyRequestParser extends CharacterParser {
  def handleChar(cursor: Char) = cursor match {
    case 'G' => new InMethodRequestParser("GET")
    case 'P' => new CharacterParser {
      override def handleChar(cursor: Char) = cursor match {
        case 'O' => new InMethodRequestParser("POST", 1)
        case 'U' => new InMethodRequestParser("PUT", 1)
        case _ => badMethod
      }
    }
    case 'D' => new InMethodRequestParser("DELETE")
    case 'H' => new InMethodRequestParser("HEAD")
    case 'O' => new InMethodRequestParser("OPTIONS")
    case 'T' => new InMethodRequestParser("TRACE")
    case 'C' => new InMethodRequestParser("CONNECT")
    case _ => badMethod
  }
}

class InMethodRequestParser(method: String, var pos: Int = 0) extends CharacterParser {
  def handleChar(cursor: Char) = {
    pos += 1
    if (pos < method.length()) {
      val current = method.charAt(pos)
      if (cursor == current) this
      else badMethod
    } else {
      if (cursor == ' ') new InUriRequestParser(method)
      else badMethod
    }
  }
}

class InUriRequestParser(method: String) extends CharacterParser {
  val uri = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (uri.length < 2048) {
      cursor match {
        case ' ' => new InVersionRequestParser(method, uri.toString)
        case _ => uri.append(cursor); this
      }
    } else new ErrorRequestParser(414, "URIs with more than 2048 characters are not supported by this server")
  }
}

class InVersionRequestParser(method: String, uri: String) extends CharacterParser {
  val version = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (version.length < 12) {
      cursor match {
        case '\r' => this
        case '\n' =>
          if (version.toString == "HTTP/1.1")
            new InHeaderNameRequestParser(method, uri, Nil)
          else badVersion
        case _ => version.append(cursor); this
      }
    } else badVersion
  }
  def badVersion = new ErrorRequestParser(400, "Http version '" + version.toString + "' not supported")
}

class InHeaderNameRequestParser(method: String, uri: String, headers: List[HttpHeader]) extends CharacterParser {
  val headerName = new JStringBuilder
  def handleChar(cursor: Char) = {
    if (headerName.length < 64) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(x); this
        case ':' => new InLwsRequestParser(new InHeaderValueRequestParser(method, uri, headers, headerName.toString))
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete
        case ' ' | '\t' | '\r' => new InLwsRequestParser(this).handleChar(cursor)
        case _ => new ErrorRequestParser(400, "Invalid character '" + cursor + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else new ErrorRequestParser(400, "HTTP headers with names longer than 64 characters are not supported by this server")
  }
  def headersComplete = {
    transferEncodingHeader match {
      case Some(encoding) => new ErrorRequestParser(501, "Non-identity transfer encodings are not currently supported by this server")
      case None => contentLengthHeader match {
        case Some(length) => bodyRequest(length)
        case None => CompleteRequestParser(method, uri, headers)
      }
    }
  }
  def transferEncodingHeader = headers.mapFindPF { case HttpHeader("Transfer-Encoding", x) if x != "identity" => x }
  def contentLengthHeader = headers.mapFindPF { case HttpHeader("Content-Length", x) => x }
  def bodyRequest(length: String) = {
    try {
      length.toInt match {
        case 0 => CompleteRequestParser(method, uri, headers)
        case x => new InBodyRequestParser(method, uri, headers, x)
      }
    } catch {
      case _: Exception => new ErrorRequestParser(400, "Invalid Content-Length header value")
    }
  }
}

class InHeaderValueRequestParser(method: String, uri: String, headers: List[HttpHeader], headerName: String)
        extends CharacterParser {
  val headerValue = new JStringBuilder
  var space = false
  def handleChar(cursor: Char) = {
    if (headerValue.length < 8192) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new InLwsRequestParser(this).handleChar(cursor)
        case '\n' => new InHeaderNameRequestParser(method, uri, HttpHeader(headerName, headerValue.toString) :: headers)
        case _ =>
          if (space) { headerValue.append(' '); space = false }
          headerValue.append(cursor)
          this
      }
    } else new ErrorRequestParser(400, "HTTP header values longer than 8192 characters are not supported by this server (" +
            "header '" + headerName + "')")
  }
}

class InLwsRequestParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case ' ' | '\t' => this
      case '\r' => new InLwsCrLfRequestParser(next)
      case x => next.handleChar(x)
    }
  }
}

class InLwsCrLfRequestParser(next: CharacterParser) extends CharacterParser {
  def handleChar(cursor: Char) = {
    cursor match {
      case '\n' => this
      case ' ' | '\t' => new InLwsRequestParser(next)
      case x => {
        // we encountered a real CRLF without following whitespace,
        // so we need to handle the newline before the current cursor
        next.handleChar('\n').asInstanceOf[CharacterParser].handleChar(x)
      }
    }
  }
}

class InBodyRequestParser(method: String, uri: String, headers: List[HttpHeader], val totalBytes: Int)
        extends IntermediateParser {
  require(totalBytes >= 0, "Content-Length must not be negative")
  val body = new Array[Byte](totalBytes)
  var bytesRead = 0
  def read(buf: ByteBuffer) = {
    val remaining = buf.remaining
    if (remaining <= totalBytes - bytesRead) {
      buf.get(body, bytesRead, remaining)
      bytesRead += remaining
      if (bytesRead == totalBytes) new CompleteRequestParser(method, uri, headers, body) else this
    } else new ErrorRequestParser(400, "Illegal Content-Length: request entity is longer than Content-Length header value")
  }
}

case class CompleteRequestParser(method: String, uri: String, headers: List[HttpHeader],
                                  body: Array[Byte] = EmptyByteArray) extends RequestParser

case class ErrorRequestParser(responseStatus: Int, message: String) extends RequestParser