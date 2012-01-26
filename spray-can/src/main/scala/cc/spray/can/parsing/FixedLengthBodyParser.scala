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
import model.{HttpHeader, MessageLine}
import java.nio.ByteBuffer

class FixedLengthBodyParser(config: HttpParserConfig, messageLine: MessageLine, headers: List[HttpHeader],
                            connectionHeader: Option[String], totalBytes: Int) extends IntermediateState {

  require(totalBytes >= 0, "Content-Length must not be negative")
  require(totalBytes <= config.maxContentLength, "HTTP message Content-Length " + totalBytes + " exceeds configured limit")

  val body = new Array[Byte](totalBytes)
  var bytesRead = 0

  def read(buf: ByteBuffer) = {
    val remaining = scala.math.min(buf.remaining, totalBytes - bytesRead)
    buf.get(body, bytesRead, remaining)
    bytesRead += remaining
    if (bytesRead == totalBytes) CompleteMessageState(messageLine, headers, connectionHeader, body) else this
  }

}