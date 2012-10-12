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

import spray.can.RequestLine
import spray.http._
import HttpProtocols._
import StatusCodes._


abstract class VersionParser extends CharacterParser {
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

  def handleSuffix(cursor: Char): ParsingState

  def badVersion = ErrorState(HTTPVersionNotSupported)
}

class RequestVersionParser(settings: ParserSettings, method: HttpMethod, uri: String) extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == '\r') { pos = 9; this } else badVersion
    case 9 => if (cursor == '\n') new HeaderNameParser(settings, RequestLine(method, uri, protocol)) else badVersion
  }
}

class EmptyResponseParser(settings: ParserSettings, isResponseToHeadRequest: Boolean) extends VersionParser {
  def handleSuffix(cursor: Char) = pos match {
    case 8 => if (cursor == ' ') new StatusCodeParser(settings, protocol, isResponseToHeadRequest) else badVersion
  }
}