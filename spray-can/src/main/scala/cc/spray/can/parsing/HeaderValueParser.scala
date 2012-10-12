/*
 * Copyright (C) 2011-2012 spray.io
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

import java.lang.{StringBuilder => JStringBuilder}
import spray.can.MessageLine
import spray.http.HttpHeaders.RawHeader


class HeaderValueParser(settings: ParserSettings, messageLine: MessageLine, headerCount: Int,
                        headers: List[RawHeader], val headerName: String) extends CharacterParser {

  val headerValue = new JStringBuilder
  var space = false

  def nameParser =
    new HeaderNameParser(settings, messageLine, headerCount + 1, RawHeader(headerName, headerValue.toString) :: headers)

  def handleChar(cursor: Char) = {
    if (headerValue.length <= settings.MaxHeaderValueLength) {
      cursor match {
        case ' ' | '\t' | '\r' => space = true; new LwsParser(this).handleChar(cursor)
        case '\n' =>
          if (headerCount < settings.MaxHeaderCount) nameParser
          else ErrorState("HTTP message header count exceeds the configured limit of " + settings.MaxHeaderCount)
        case _ =>
          if (space) {headerValue.append(' '); space = false}
          headerValue.append(cursor)
          this
      }
    } else {
      ErrorState("HTTP header value exceeds the configured limit of " + settings.MaxHeaderValueLength +
                  " characters", "header '" + headerName + "'")
    }
  }
}