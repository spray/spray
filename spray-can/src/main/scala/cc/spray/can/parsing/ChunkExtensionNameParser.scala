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
import spray.http.ChunkExtension


class ChunkExtensionNameParser(settings: ParserSettings, chunkSize: Int, extCount: Int = 0,
                               extensions: List[ChunkExtension] = Nil) extends CharacterParser {

  val extName = new JStringBuilder

  def handleChar(cursor: Char) = {
    if (extName.length <= settings.MaxChunkExtNameLength) {
      cursor match {
        case x if isTokenChar(x) => extName.append(x); this
        case '=' => new ChunkExtensionValueParser(settings, chunkSize, extCount, extensions, extName.toString)
        case ' ' | '\t' => this
        case _ => ErrorState("Invalid character '" + escape(cursor) + "', expected TOKEN CHAR, SPACE, TAB or EQUAL")
      }
    } else {
      ErrorState("Chunk extension name exceeds the configured limit of " + settings.MaxChunkExtNameLength +
                  " characters", "extension '" + extName.toString.take(50) + "...'")
    }
  }

}