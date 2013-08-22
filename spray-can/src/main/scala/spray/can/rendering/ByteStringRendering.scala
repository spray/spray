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
import akka.util.ByteString

private[can] class ByteStringRendering(sizeHint: Int) extends Rendering {
  private[this] var array = new Array[Byte](sizeHint)
  private[this] var size = 0

  def ~~(char: Char): this.type = {
    val oldSize = growBy(1)
    array(oldSize) = char.toByte
    this
  }

  def ~~(bytes: Array[Byte]): this.type = {
    if (bytes.length > 0) {
      val oldSize = growBy(bytes.length)
      System.arraycopy(bytes, 0, array, oldSize, bytes.length)
    }
    this
  }

  private def growBy(delta: Int): Int = {
    val oldSize = size
    val neededSize = oldSize.toLong + delta
    if (array.length < neededSize)
      if (neededSize < Int.MaxValue) {
        val newLen = math.min(math.max(array.length.toLong * 2, neededSize), Int.MaxValue).toInt
        val newArray = new Array[Byte](newLen)
        System.arraycopy(array, 0, newArray, 0, array.length)
        array = newArray
      } else sys.error("Cannot create compact ByteString greater than 2GB in size")
    size = neededSize.toInt
    oldSize
  }

  def get: ByteString = akka.spray.createByteStringUnsafe(array, 0, size)
}