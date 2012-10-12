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


class ChunkExtensionValueParser(settings: ParserSettings, chunkSize: Int, extCount: Int,
                                extensions: List[ChunkExtension], extName: String) extends CharacterParser {

  val extValue = new JStringBuilder
  var quoted = false

  def next(parser: ParsingState) = if (extCount < settings.MaxChunkExtCount) {
    parser
  } else {
    ErrorState("Chunk extension count exceeds the configured limit of " + settings.MaxChunkExtCount)
  }

  def newExtensions = ChunkExtension(extName, extValue.toString) :: extensions

  def handleChar(cursor: Char) = {
    if (extValue.length <= settings.MaxChunkExtValueLength) {
      if (quoted) {
        cursor match {
          case '"' => quoted = false; this
          case '\r' | '\n' => ErrorState("Invalid chunk extension value: unclosed quoted string")
          case x => extValue.append(x); this
        }
      } else {
        cursor match {
          case x if isTokenChar(x) => extValue.append(x); this
          case '"' if extValue.length == 0 => quoted = true; this
          case ' ' | '\t' | '\r' => this
          case ';' => next(new ChunkExtensionNameParser(settings, chunkSize, extCount + 1, newExtensions))
          case '\n' => next {
            if (chunkSize == 0) new TrailerParser(settings, newExtensions)
            else new ChunkBodyParser(settings, chunkSize, newExtensions)
          }
          case _ => ErrorState("Invalid character '" + escape(cursor) + "', expected TOKEN CHAR, SPACE, TAB or EQUAL")
        }
      }
    } else {
      ErrorState("Chunk extension value exceeds the configured limit of " + settings.MaxChunkExtValueLength +
                  " characters", "extension '" + extName + "'")
    }
  }

}