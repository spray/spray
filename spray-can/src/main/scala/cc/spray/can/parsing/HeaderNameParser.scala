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

package cc.spray.can.parsing

import java.lang.{StringBuilder => JStringBuilder}
import annotation.tailrec
import cc.spray.can.{StatusLine, RequestLine, MessageLine}
import cc.spray.http.{ContentType, HttpProtocols}
import cc.spray.http.HttpHeaders.RawHeader
import HttpProtocols._
import cc.spray.http.parser.HttpParser


class HeaderNameParser(settings: ParserSettings, messageLine: MessageLine, headerCount: Int = 0,
                       headers: List[RawHeader] = Nil) extends CharacterParser {

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

  def headersComplete = traverse(headers, None, None, None, None, false, false)

  @tailrec
  private def traverse(rest: List[RawHeader],
                       cHeader: Option[String],  // connection header
                       clHeader: Option[String], // content-length header
                       ctHeader: Option[ContentType], // content-type header
                       teHeader: Option[String], // transfer-encoding header
                       hostPresent: Boolean,
                       e100Present: Boolean): ParsingState = {
    rest match {
      case Nil =>
        val next = nextState(cHeader, clHeader, ctHeader, teHeader, hostPresent)
        if (e100Present) Expect100ContinueState(next) else next

      case RawHeader("content-length", value) :: tail =>
        if (clHeader.isEmpty) {
          traverse(tail, cHeader, Some(value), ctHeader, teHeader, hostPresent, e100Present)
        } else ErrorState("HTTP message must not contain more than one Content-Length header", 400)

      case RawHeader("content-type", value) :: tail =>
        if (ctHeader.isEmpty) HttpParser.parseContentType(value) match {
          case Right(ct) => traverse(tail, cHeader, clHeader, Some(ct), teHeader, hostPresent, e100Present)
          case Left(error) => ErrorState("Illegal Content-Type header: " + error, 400)
        } else ErrorState("HTTP message must not contain more than one Content-Type header", 400)

      case RawHeader("host", _) :: tail =>
        if (!hostPresent) traverse(tail, cHeader, clHeader, ctHeader, teHeader, true, e100Present)
        else ErrorState("HTTP message must not contain more than one Host header", 400)

      case RawHeader("connection", value) :: tail =>
        traverse(tail, Some(value), clHeader, ctHeader, teHeader, hostPresent, e100Present)

      case RawHeader("transfer-encoding", value) :: tail =>
        traverse(tail, cHeader, clHeader, ctHeader, Some(value), hostPresent, e100Present)

      case RawHeader("expect", value) :: tail =>
        if (value.toLowerCase == "100-continue") traverse(tail, cHeader, clHeader, ctHeader, teHeader, hostPresent, true)
        else ErrorState("Expectation '" + value + "' is not supported by this server", 417)

      case _ :: tail => traverse(tail, cHeader, clHeader, ctHeader, teHeader, hostPresent, e100Present)
    }
  }

  // TODO: responses to HEAD requests also never have a response body!! This must be handled here!
  private def nextState(cHeader: Option[String], clHeader: Option[String], ctHeader: Option[ContentType],
                        teHeader: Option[String], hostPresent: Boolean) = {
    // rfc2616 sec. 4.4
    messageLine match {
      case RequestLine(_, _, `HTTP/1.1`) if !hostPresent =>
        ErrorState("Host header required", 400)

      // certain responses never have a body
      case StatusLine(_, status, _, hr) if hr || (100 < status && status <= 199 && status > 100) || status == 204 || status == 304 =>
        CompleteMessageState(messageLine, headers, cHeader)

      case _ if teHeader.isDefined && teHeader.get.toLowerCase != "identity" =>
        ChunkedStartState(messageLine, headers, cHeader, ctHeader)

      case _ if clHeader.isDefined => clHeader.get match {
        case "0" => CompleteMessageState(messageLine, headers, cHeader, ctHeader)
        case value =>
          try new FixedLengthBodyParser(settings, messageLine, headers, cHeader, ctHeader, value.toInt)
          catch { case e: Exception => ErrorState("Invalid Content-Length header value: " + e.getMessage) }
      }

      case _: RequestLine => CompleteMessageState(messageLine, headers, cHeader, ctHeader)

      case x: StatusLine if cHeader.isDefined && cHeader.get.toLowerCase == "close" || cHeader.isEmpty && x.protocol == `HTTP/1.0` =>
        new ToCloseBodyParser(settings, messageLine, headers, cHeader, ctHeader)

      case _ => ErrorState("Content-Length header or chunked transfer encoding required", 411)
    }
  }
}