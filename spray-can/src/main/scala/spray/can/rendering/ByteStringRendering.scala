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

package spray.can.rendering

import spray.http.Rendering
import akka.util.{ ByteStringBuilder, ByteString }

private[can] class ByteStringRendering(sizeHint: Int) extends Rendering {
  private[this] val b = new ByteStringBuilder
  b.sizeHint(sizeHint)

  def ~~(char: Char): this.type = {
    b.+=(char.toByte)
    this
  }

  def ~~(bytes: Array[Byte]): this.type = {
    b.++=(bytes)
    this
  }

  def get: ByteString = b.result()
}
