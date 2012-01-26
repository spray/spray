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
import model.{MessageLine, HttpHeader}
import java.nio.ByteBuffer

class ToCloseBodyParser(config: HttpParserConfig, messageLine: MessageLine, headers: List[HttpHeader],
                        connectionHeader: Option[String]) extends IntermediateState {

  private var body: Array[Byte] = EmptyByteArray

  def read(buf: ByteBuffer) = {
    val array = new Array[Byte](buf.remaining)
    buf.get(array)
    body match {
      case EmptyByteArray => body = array; this
      case _ => {
        if (body.length + array.length <= config.maxContentLength) {
          body = body concat array
          this
        } else ErrorState("HTTP message body size exceeds configured limit", 413)
      }
    }
  }

  def complete = CompleteMessageState(messageLine, headers, connectionHeader, body)
}