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


class ChunkParser(settings: ParserSettings) extends CharacterParser {
  var chunkSize = -1

  def handle(digit: Int) = {
    chunkSize = if (chunkSize == -1) digit else chunkSize * 16 + digit
    if (chunkSize > settings.MaxChunkSize)
      ErrorState("HTTP message chunk size exceeds the configured limit of " + settings.MaxChunkSize + " bytes")
    else this
  }

  def handleChar(cursor: Char) = cursor match {
    case x if '0' <= cursor && cursor <= '9' => handle(x - '0')
    case x if 'A' <= cursor && cursor <= 'F' => handle(x - 'A' + 10)
    case x if 'a' <= cursor && cursor <= 'f' => handle(x - 'a' + 10)
    case ' ' | '\t' | '\r' => this
    case '\n' => chunkSize match {
      case -1 => ErrorState("Chunk size expected")
      case 0 => new TrailerParser(settings)
      case _ => new ChunkBodyParser(settings, chunkSize)
    }
    case ';'  => new ChunkExtensionNameParser(settings, chunkSize)
    case _ => ErrorState("Illegal chunk size")
  }

}