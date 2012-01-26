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

import config.HttpParserConfig
import model.HttpMethod

class MethodParser(config: HttpParserConfig, method: HttpMethod, var pos: Int = 0) extends CharacterParser {

  def handleChar(cursor: Char) = {
    pos += 1
    if (pos < method.name.length()) {
      val current = method.name.charAt(pos)
      if (cursor == current) {
        this
      }
      else {
        badMethod
      }
    } else {
      if (cursor == ' ') {
        new UriParser(config, method)
      }
      else {
        badMethod
      }
    }
  }

}