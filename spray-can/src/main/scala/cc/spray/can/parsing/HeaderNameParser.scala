/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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
import config.HttpParserConfig
import annotation.tailrec
import model._
import HttpProtocols._

class HeaderNameParser(config: HttpParserConfig, messageLine: MessageLine, headerCount: Int = 0,
                       headers: List[HttpHeader] = Nil) extends CharacterParser {

  val headerName = new JStringBuilder

  def valueParser = new HeaderValueParser(config, messageLine, headerCount, headers, headerName.toString)

  def handleChar(cursor: Char) = {
    if (headerName.length <= config.maxHeaderNameLength) {
      cursor match {
        case x if util.isTokenChar(x) => headerName.append(x); this
        case ':' => new LwsParser(valueParser)
        case '\r' if headerName.length == 0 => this
        case '\n' if headerName.length == 0 => headersComplete
        case ' ' | '\t' | '\r' => new LwsParser(this).handleChar(cursor)
        case _ => ErrorState("Invalid character '" + cursor + "', expected TOKEN CHAR, LWS or COLON")
      }
    } else {
      ErrorState("HTTP headers with names longer than " + config.maxHeaderNameLength + " characters are not supported")
    }
  }

  def headersComplete = traverse(headers, None, None, None, false)

  @tailrec
  private def traverse(remaining: List[HttpHeader], connection: Option[String], contentLength: Option[String],
               transferEncoding: Option[String], hostHeaderPresent: Boolean): ParsingState = {
    if (!remaining.isEmpty) {
      remaining.head.name match {
        case "Content-Length" =>
          if (contentLength.isEmpty) {
            traverse(remaining.tail, connection, Some(remaining.head.value), transferEncoding, hostHeaderPresent)
          } else ErrorState("HTTP message must not contain more than one Content-Length header", 400)
        case "Transfer-Encoding" => traverse(remaining.tail, connection, contentLength, Some(remaining.head.value), hostHeaderPresent)
        case "Connection" => traverse(remaining.tail, Some(remaining.head.value), contentLength, transferEncoding, hostHeaderPresent)
        case "Host" =>
          if (!hostHeaderPresent) traverse(remaining.tail, connection, contentLength, transferEncoding, true)
          else ErrorState("HTTP message must not contain more than one Host header", 400)
        case _ => traverse(remaining.tail, connection, contentLength, transferEncoding, hostHeaderPresent)
      }
    } else messageLine match { // rfc2616 sec. 4.4
      case x: RequestLine if x.protocol == `HTTP/1.1` && !hostHeaderPresent =>
        ErrorState("Host header required", 400)
      case _ if messageBodyDisallowed =>
        CompleteMessageState(messageLine, headers, connection)
      case _ if transferEncoding.isDefined && transferEncoding.get != "identity" =>
        ChunkedStartState(messageLine, headers, connection)
      case _ if contentLength.isDefined =>
        contentLength.get match {
          case "0" => CompleteMessageState(messageLine, headers, connection)
          case value => try {new FixedLengthBodyParser(config, messageLine, headers, connection, value.toInt)}
          catch {case e: Exception => ErrorState("Invalid Content-Length header value: " + e.getMessage)}
        }
      case _: RequestLine => CompleteMessageState(messageLine, headers, connection)
      case x: StatusLine if connection == Some("close") || connection.isEmpty && x.protocol == `HTTP/1.0` =>
        new ToCloseBodyParser(config, messageLine, headers, connection)
      case _ => ErrorState("Content-Length header or chunked transfer encoding required", 411)
    }
  }

  def messageBodyDisallowed = messageLine match {
    case _: RequestLine => false // there can always be a body in a request
    case StatusLine(requestMethod, _, status, _) => // certain responses never have a body
      (status / 100 == 1) || status == 204 || status == 304 || requestMethod == HttpMethods.HEAD
  }
}