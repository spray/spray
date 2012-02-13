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
import model.{HttpHeader, MessageLine}

class HeaderValueParser(config: HttpParserConfig, messageLine: MessageLine, headerCount: Int,
                        headers: List[HttpHeader], val headerName: String) extends CharacterParser {

  val headerValue = new JStringBuilder
  var space = false

  def nameParser =
    new HeaderNameParser(config, messageLine, headerCount + 1, HttpHeader(headerName, headerValue.toString) :: headers)

  def handleChar(cursor: Char) = {
    if (headerValue.length <= config.maxHeaderValueLength) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new LwsParser(this).handleChar(cursor)
        case '\n' =>
          if (headerCount < config.maxHeaderCount) nameParser
          else ErrorParser("HTTP message header count exceeds the configured limit of " + config.maxHeaderCount, 400)
        case _ =>
          if (space) {headerValue.append(' '); space = false}
          headerValue.append(cursor)
          this
      }
    } else {
      ErrorParser("HTTP header value exceeds the configured limit of " + config.maxHeaderValueLength +
                  " characters (header '" + headerName + "')")
    }
  }
}