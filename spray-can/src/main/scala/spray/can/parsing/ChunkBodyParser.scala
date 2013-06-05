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

import annotation.tailrec
import java.nio.ByteBuffer
import spray.http.ChunkExtension


class ChunkBodyParser(settings: ParserSettings, chunkSize: Int,
                      extensions: List[ChunkExtension] = Nil) extends IntermediateState {

  require(chunkSize > 0, "Chunk size must not be negative")
  require(chunkSize <= settings.MaxChunkSize,
          "HTTP message chunk size " + chunkSize + " exceeds configured limit of " + settings.MaxChunkSize)

  val body = new Array[Byte](chunkSize)
  var bytesRead = 0

  @tailrec
  final def read(buf: ByteBuffer) = {
    if (bytesRead < chunkSize) {
      val remaining = scala.math.min(buf.remaining, chunkSize - bytesRead)
      buf.get(body, bytesRead, remaining)
      bytesRead += remaining
      if (buf.remaining == 0) this else read(buf)
    } else if (bytesRead == chunkSize) {
      if (buf.get() == '\r'.asInstanceOf[Byte]) {
        bytesRead += 1
        if (buf.remaining == 0) this else read(buf)
      } else ErrorState("Expected CR after message chunk")
    } else {
      if (buf.get() == '\n'.asInstanceOf[Byte]) ChunkedChunkState(extensions, body)
      else ErrorState("Expected LF after CR after message chunk")
    }
  }
}