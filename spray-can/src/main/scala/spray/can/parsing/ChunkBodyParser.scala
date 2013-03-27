/*
 * Copyright (C) 2011-2013 spray.io
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

import scala.annotation.tailrec
import akka.util.ByteIterator
import spray.http.ChunkExtension

class ChunkBodyParser(settings: ParserSettings, chunkSize: Int,
                      extensions: List[ChunkExtension] = Nil) extends IntermediateState {

  require(chunkSize > 0, "Chunk size must not be negative")
  require(chunkSize <= settings.maxChunkSize,
    "HTTP message chunk size " + chunkSize + " exceeds configured limit of " + settings.maxChunkSize)

  val body = new Array[Byte](chunkSize)
  var bytesRead = 0

  @tailrec
  final def read(data: ByteIterator) = {
    if (bytesRead < chunkSize) {
      val remaining = scala.math.min(data.len, chunkSize - bytesRead)
      data.getBytes(body, bytesRead, remaining)
      bytesRead += remaining
      if (!data.hasNext) this else read(data)
    } else if (bytesRead == chunkSize) {
      if (data.next() == '\r'.asInstanceOf[Byte]) {
        bytesRead += 1
        if (!data.hasNext) this else read(data)
      } else ErrorState("Expected CR after message chunk")
    } else {
      if (data.next() == '\n'.asInstanceOf[Byte]) ChunkedChunkState(extensions, body)
      else ErrorState("Expected LF after CR after message chunk")
    }
  }
}