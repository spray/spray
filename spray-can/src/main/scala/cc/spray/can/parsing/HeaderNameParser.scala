/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.can
package parsing

import java.lang.{StringBuilder => JStringBuilder}
import annotation.tailrec
import model._
import HttpProtocols._

class HeaderNameParser(settings: ParserSettings, messageLine: MessageLine, headerCount: Int = 0,
                       headers: List[HttpHeader] = Nil) extends CharacterParser {

  val headerName = new JStringBuilder

  def valueParser = new HeaderValueParser(settings, messageLine, headerCount, headers, headerName.toString)

  def handleChar(cursor: Char) = {
    if (headerName.length <= settings.MaxHeaderNameLength) {
      cursor match {
        case x if isTokenChar(x) => headerName.append(toLowerCase(x)); this
        case ':' => new LwsParser(valueParser)
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete
        case ' ' | '\t' | '\r' => new LwsParser(this).handleChar(cursor)
        case _ => ErrorState("Invalid character '" + escape(cursor) + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else {
      ErrorState("HTTP header name exceeds the configured limit of " + settings.MaxHeaderNameLength +
                  " characters (" + headerName.toString.take(50) + "...)")
    }
  }

  def toLowerCase(c: Char) = if ('A' <= c && c <= 'Z') (c + 32).toChar else c

  def headersComplete = traverse(headers, None, None, None, false, false)

  @tailrec
  private def traverse(rest: List[HttpHeader], cHeader: Option[String], clHeader: Option[String],
                       teHeader: Option[String], hostPresent: Boolean, e100Present: Boolean): ParsingState = {
    rest match {
      case Nil =>
        val next = nextState(cHeader, clHeader, teHeader, hostPresent)
        if (e100Present) Expect100ContinueState(next) else next

      case HttpHeader("content-length", value) :: tail =>
        if (clHeader.isEmpty) {
          traverse(tail, cHeader, Some(value), teHeader, hostPresent, e100Present)
        } else ErrorState("HTTP message must not contain more than one Content-Length header", 400)

      case HttpHeader("host", _) :: tail =>
        if (!hostPresent) traverse(tail, cHeader, clHeader, teHeader, true, e100Present)
        else ErrorState("HTTP message must not contain more than one Host header", 400)

      case HttpHeader("connection", value) :: tail =>
        traverse(tail, Some(value), clHeader, teHeader, hostPresent, e100Present)

      case HttpHeader("transfer-encoding", value) :: tail =>
        traverse(tail, cHeader, clHeader, Some(value), hostPresent, e100Present)

      case HttpHeader("expect", value) :: tail =>
        if (value == "100-continue") traverse(tail, cHeader, clHeader, teHeader, hostPresent, true)
        else ErrorState("Expectation '" + value + "' is not supported by this server", 417)

      case _ :: tail => traverse(tail, cHeader, clHeader, teHeader, hostPresent, e100Present)
    }
  }

  private def nextState(cHeader: Option[String], clHeader: Option[String], teHeader: Option[String],
                        hostPresent: Boolean) = {
    // rfc2616 sec. 4.4
    messageLine match {
      case RequestLine(_, _, `HTTP/1.1`) if !hostPresent =>
        ErrorState("Host header required", 400)

      // certain responses never have a body
      case StatusLine(_, status, _) if (status <= 199 && status > 100) || status == 204 || status == 304 =>
        CompleteMessageState(messageLine, headers, cHeader)

      case _ if teHeader.isDefined && teHeader.get != "identity" =>
        ChunkedStartState(messageLine, headers, cHeader)

      case _ if clHeader.isDefined => clHeader.get match {
        case "0" => CompleteMessageState(messageLine, headers, cHeader)
        case value =>
          try new FixedLengthBodyParser(settings, messageLine, headers, cHeader, value.toInt)
          catch { case e: Exception => ErrorState("Invalid Content-Length header value: " + e.getMessage) }
      }

      case _: RequestLine => CompleteMessageState(messageLine, headers, cHeader)

      case x: StatusLine if cHeader.isDefined && cHeader.get == "close" || cHeader.isEmpty && x.protocol == `HTTP/1.0` =>
        new ToCloseBodyParser(settings, messageLine, headers, cHeader)

      case _ => ErrorState("Content-Length header or chunked transfer encoding required", 411)
    }
  }
}