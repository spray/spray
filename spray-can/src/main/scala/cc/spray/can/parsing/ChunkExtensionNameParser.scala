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
import model.ChunkExtension

class ChunkExtensionNameParser(config: HttpParserConfig, chunkSize: Int, extCount: Int = 0,
                               extensions: List[ChunkExtension] = Nil) extends CharacterParser {

  val extName = new JStringBuilder

  def handleChar(cursor: Char) = {
    if (extName.length <= config.maxChunkExtNameLength) {
      cursor match {
        case x if util.isTokenChar(x) => extName.append(x); this
        case '=' => new ChunkExtensionValueParser(config, chunkSize, extCount, extensions, extName.toString)
        case ' ' | '\t' => this
        case _ => ErrorState("Invalid character '" + cursor + "', expected TOKEN CHAR, SPACE, TAB or EQUAL")
      }
    } else {
      ErrorState("Chunk extensions with names longer than " + config.maxChunkExtNameLength +
              " characters are not supported")
    }
  }

}