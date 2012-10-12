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

import java.nio.ByteBuffer
import spray.can.MessageLine
import spray.http.HttpHeaders.RawHeader
import spray.http.ContentType


class FixedLengthBodyParser(messageLine: MessageLine,
                            headers: List[RawHeader],
                            connectionHeader: Option[String],
                            contentType: Option[ContentType],
                            totalBytes: Int) extends IntermediateState {

  require(totalBytes >= 0, "Content-Length must not be negative")

  val body = new Array[Byte](totalBytes)
  var bytesRead = 0

  def read(buf: ByteBuffer) = {
    val remaining = scala.math.min(buf.remaining, totalBytes - bytesRead)
    buf.get(body, bytesRead, remaining)
    bytesRead += remaining
    if (bytesRead == totalBytes) CompleteMessageState(messageLine, headers, connectionHeader, contentType, body)
    else this
  }

}