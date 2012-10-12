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
import spray.http.HttpProtocol
import spray.can.StatusLine


class ReasonParser(settings: ParserSettings, protocol: HttpProtocol, status: Int, isResponseToHeadRequest: Boolean)
    extends CharacterParser {
  val reason = new JStringBuilder

  def handleChar(cursor: Char) = {
    if (reason.length <= settings.MaxResponseReasonLength) {
      cursor match {
        case '\r' => this
        case '\n' => new HeaderNameParser(settings,
          StatusLine(protocol, status, reason.toString, isResponseToHeadRequest))
        case _ => reason.append(cursor); this
      }
    } else {
      ErrorState("Reason phrase exceeds the configured limit of " + settings.MaxResponseReasonLength + " characters")
    }
  }

}