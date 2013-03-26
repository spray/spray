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

package spray.util.pimps

import java.nio.ByteBuffer

class PimpedByteBuffer(underlying: ByteBuffer) {

  /**
   * Converts the contents of the underlying ByteBuffer into a String using the US-ASCII charset.
   */
  def drainToString: String = {
    val sb = new java.lang.StringBuilder
    while (underlying.remaining > 0) sb.append(underlying.get.toChar)
    sb.toString
  }

  /**
   * Copies the current contents of the underlying buffer into a new ByteBuffer.
   */
  def copy: ByteBuffer = {
    if (!underlying.hasArray) throw new IllegalArgumentException
    val array = new Array[Byte](underlying.remaining)
    System.arraycopy(underlying.array, underlying.position, array, 0, underlying.remaining)
    ByteBuffer.wrap(array)
  }

  /**
   * Appends the contents of the given buffer to the contents of the underlying buffer and returns
   * the result as a new ByteBuffer.
   */
  def concat(buf: ByteBuffer): ByteBuffer = {
    if (underlying.remaining == 0) buf
    else if (buf.remaining == 0) underlying
    else {
      if (!underlying.hasArray || !buf.hasArray) throw new IllegalArgumentException
      val array = new Array[Byte](underlying.remaining + buf.remaining)
      System.arraycopy(underlying.array, underlying.position, array, 0, underlying.remaining)
      System.arraycopy(buf.array, buf.position, array, underlying.remaining, buf.remaining)
      ByteBuffer.wrap(array)
    }
  }
}