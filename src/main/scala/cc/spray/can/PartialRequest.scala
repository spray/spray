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
import java.lang.{IllegalStateException, StringBuilder => JStringBuilder}

sealed trait PartialRequest {
  def read(buf: ByteBuffer): PartialRequest
}

abstract class InHeadPartialRequest extends PartialRequest {
  var line = 1
  var pos = 0
  var cursor = '\0'

  def read(buf: ByteBuffer): PartialRequest = {
    def readChar() {
      if (cursor == '\n') {
        line += 1
        pos = 1
      } else pos += 1
      cursor = buf.get().asInstanceOf[Char] // simple US-ASCII encoding conversion
    }

    if (buf.remaining > 0) {
      readChar()
      val Self = this
      handleChar() match {
        case Self => this
        case req: InHeadPartialRequest =>
          req.line = line
          req.pos = pos
          req.cursor = cursor
          req
        case req => req
      }
    } else this
  }

  def isTokenChar(c: Char) = c match {
    case x if 'a' <= x && x <= 'z' => true
    case x if 'A' <= x && x <= 'Z' => true
    case '-' => true
    case '(' | ')' | '<' | '>' | '@' | ',' | ';' | ':' | '\\' | '"' | '/' | '[' | ']' | '?' | '=' | '{' | '}' => false
    case x => 32 < x && x < 127
  }

  def badMethod = new ErrorRequest(501, "Unsupported HTTP method")

  def handleChar(): PartialRequest
}

object EmptyRequest extends InHeadPartialRequest {
  def handleChar() = cursor match {
    case 'G' => new InMethodPartialRequest("GET")
    case 'P' => new InMethodPartialRequest("POST") {
      override def handleChar() = cursor match {
        case 'O' => new InMethodPartialRequest("POST")
        case 'U' => new InMethodPartialRequest("PUT")
        case _ => badMethod
      }
    }
    case 'D' => new InMethodPartialRequest("DELETE")
    case 'H' => new InMethodPartialRequest("HEAD")
    case 'O' => new InMethodPartialRequest("OPTIONS")
    case 'T' => new InMethodPartialRequest("TRACE")
    case 'C' => new InMethodPartialRequest("CONNECT")
    case _ => badMethod
  }
}

class InMethodPartialRequest(method: String) extends InHeadPartialRequest {
  def handleChar() = {
    if (pos <= method.length()) {
      val current = method.charAt(pos - 1)
      if (cursor == current) this
      else badMethod
    } else {
      if (cursor == ' ') new InUriPartialRequest(method)
      else badMethod
    }
  }
}

class InUriPartialRequest(method: String) extends InHeadPartialRequest {
  val uri = new JStringBuilder
  def handleChar() = {
    if (uri.length < 2048) {
      cursor match {
        case ' ' => new InVersionPartialRequest(method, uri.toString)
        case x => uri.append(x); this
      }
    } else new ErrorRequest(414, "URIs with more than 2048 characters are not supported by this server")
  }
}

class InVersionPartialRequest(method: String, uri: String) extends InHeadPartialRequest {
  val version = new JStringBuilder
  def handleChar() = {
    if (version.length < 12) {
      cursor match {
        case '\r' => this
        case '\n' =>
          if (version.toString == "HTTP/1.1")
            new InHeaderNamePartialRequest(method, uri, Nil)
          else badVersion
        case x => version.append(x); this
      }
    } else badVersion
  }
  def badVersion = new ErrorRequest(400, "Http version '" + version.toString + "' not supported")
}

class InHeaderNamePartialRequest(method: String, uri: String, headers: List[HttpHeader]) extends InHeadPartialRequest {
  val headerName = new JStringBuilder
  def handleChar() = {
    if (headerName.length < 64) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(x); this
        case ':' => new InLwsPartialRequest(new InHeaderValuePartialRequest(method, uri, headers, headerName.toString))
        case ' ' | '\t' | '\r' => new InLwsPartialRequest(this)
        case '\n' if headerName.length == 0 => headersComplete
        case _ => new ErrorRequest(400, "Invalid character, expected TOKEN CHAR, LWS or COLON")
      }
    } else new ErrorRequest(400, "HTTP headers with names longer than 64 characters are not supported by this server")
  }
  def headersComplete = {
    transferEncodingHeader match {
      case Some(encoding) => new ErrorRequest(501, "Non-identity transfer encodings are not currently supported by this server")
      case None => contentLengthHeader match {
        case Some(length) => bodyRequest(length)
        case None => new ErrorRequest(411, "Content-Length header required but not present in the request")
      }
    }
  }
  def transferEncodingHeader = headers.mapFindPF { case HttpHeader("Transfer-Encoding", x) if x != "identity" => x }
  def contentLengthHeader = headers.mapFindPF { case HttpHeader("Content-Length", x) => x }
  def bodyRequest(length: String) = {
    try {
      length.toInt match {
        case 0 => CompletePartialRequest(method, uri, headers)
        case x => new InBodyPartialRequest(method, uri, headers, x)
      }
    } catch {
      case _: Exception => new ErrorRequest(400, "Invalid Content-Length header value")
    }
  }
}

class InHeaderValuePartialRequest(method: String, uri: String, headers: List[HttpHeader], headerName: String)
        extends InHeadPartialRequest {
  val headerValue = new JStringBuilder
  var space = false
  def handleChar() = {
    if (headerValue.length < 8192) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new InLwsPartialRequest(this)
        case '\n' => new InHeaderNamePartialRequest(method, uri, HttpHeader(headerName, headerValue.toString) :: headers)
        case x =>
          if (space) { headerValue.append(' '); space = false }
          headerValue.append(x)
          this
      }
    } else new ErrorRequest(400, "HTTP header values longer than 8192 characters are not supported by this server (" +
            "header '" + headerName + "')")
  }
}

class InLwsPartialRequest(next: InHeadPartialRequest) extends InHeadPartialRequest {
  def handleChar() = {
    cursor match {
      case ' ' | '\t' => this
      case '\r' => new InLwsCrLfPartialRequest(next)
      case x => next.handleChar()
    }
  }
}

class InLwsCrLfPartialRequest(next: InHeadPartialRequest) extends InHeadPartialRequest {
  var nLine = 0
  var nPos = 0
  def handleChar() = {
    cursor match {
      case '\n' => nLine = line; nPos = pos; this
      case ' ' | '\t' => new InLwsPartialRequest(next)
      case x => {
        // we encountered a real CRLF without following whitespace,
        // so we need to handle the newline before the current cursor
        val savedLine = line; line = nLine; val savedPos = pos; pos = nPos; cursor = '\n'
        val next2 = next.handleChar().asInstanceOf[InHeadPartialRequest] // handle the newline
        line = savedLine; pos = savedPos; cursor = x
        next2.handleChar() // handle the x
      }
    }
  }
}

class InBodyPartialRequest(method: String, uri: String, headers: List[HttpHeader], val totalBytes: Int)
        extends PartialRequest {
  val body = new Array[Byte](totalBytes)
  var bytesRead = 0
  def read(buf: ByteBuffer) = {
    val remaining = buf.remaining
    if (remaining <= totalBytes - bytesRead) {
      buf.get(body, bytesRead, remaining)
      bytesRead += remaining
      if (bytesRead == totalBytes) new CompletePartialRequest(method, uri, headers, body) else this
    } else new ErrorRequest(400, "Illegal Content-Length: request entity is longer than Content-Length header value")
  }
}

case class CompletePartialRequest(method: String, uri: String, headers: List[HttpHeader],
                                  body: Array[Byte] = EmptyByteArray) extends PartialRequest {
  def read(buf: ByteBuffer) = throw new IllegalStateException
}

case class ErrorRequest(responseStatus: Int, message: String) extends PartialRequest {
  def read(buf: ByteBuffer) = throw new IllegalStateException
}