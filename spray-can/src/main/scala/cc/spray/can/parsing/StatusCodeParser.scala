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

import spray.http.HttpProtocol


class StatusCodeParser(settings: ParserSettings, protocol: HttpProtocol, isResponseToHeadRequest: Boolean)
    extends CharacterParser {
  var pos = 0
  var status = 0

  def handleChar(cursor: Char) = pos match {
    case 0 => if ('1' <= cursor && cursor <= '5') { pos = 1; status = (cursor - '0') * 100; this } else badStatus
    case 1 => if ('0' <= cursor && cursor <= '9') { pos = 2; status += (cursor - '0') * 10; this } else badStatus
    case 2 => if ('0' <= cursor && cursor <= '9') { pos = 3; status += cursor - '0'; this } else badStatus
    case 3 => if (cursor == ' ') new ReasonParser(settings, protocol, status, isResponseToHeadRequest) else badStatus
  }

  def badStatus = ErrorState("Illegal response status code")
}