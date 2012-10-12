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

package spray.io

import java.nio.charset.Charset
import java.nio.ByteBuffer

/**
 * A fast, mutable builder for byte arrays and heap-based ByteBuffers.
 */
class BufferBuilder protected(initialSize: Int, array: Array[Byte]) {
  if (initialSize > array.length) throw new IllegalArgumentException
  private[this] var _array = array
  private[this] var _size = initialSize

  def remainingCapacity: Int = _array.length - _size

  def size: Int = _size

  def append(bytes: Array[Byte]): this.type = append(bytes, 0)
  def append(bytes: Array[Byte], padding: Int): this.type = {
    val bl = bytes.length
    if (bl > 0) {
      if (_size > 0) {
        ensureAdditionalCapacity(bl, padding)
        System.arraycopy(bytes, 0, _array, _size, bl)
        _size += bl
      } else {
        _array = bytes
        _size = bl
      }
    }
    this
  }

  def append(string: String, charset: Charset): this.type = append(string, charset, 0)
  def append(string: String, charset: Charset, padding: Int): this.type = {
    if (string.isEmpty) this else append(string.getBytes(charset), padding)
  }

  /**
   * Appends a string in ASCII encoding. (faster than going through a charset)
   */
  def append(string: String): this.type = append(string, 0)
  def append(string: String, padding: Int): this.type = {
    val sl = string.length
    if (sl > 0) {
      ensureAdditionalCapacity(sl, padding)
      var i = 0
      while (i < sl) {
        _array(_size) = string.charAt(i).asInstanceOf[Byte]
        i += 1
        _size += 1
      }
    }
    this
  }

  def append(byte: Byte): this.type = append(byte, 0)
  def append(byte: Byte, padding: Int): this.type = {
    ensureAdditionalCapacity(1, padding)
    _array(_size) = byte
    _size += 1
    this
  }

  def append(char: Char): this.type = append(char, 0)
  def append(char: Char, padding: Int): this.type = {
    append(char.asInstanceOf[Byte])
  }

  /**
   * Gets the contents of the builder as a byte array.
   * CAUTION: For performance reasons the content might not be defensively copied!
   */
  def toArray: Array[Byte] = {
    if (size < _array.length) {
      val array = new Array[Byte](size)
      System.arraycopy(_array, 0, array, 0, size)
      array
    } else _array
  }

  /**
   * Wraps the underlying array in a ByteBuffer.
   * CAUTION: For performance reasons the underlying array is not defensively copied!
   */
  def toByteBuffer: ByteBuffer = {
    val bb = ByteBuffer.wrap(_array)
    bb.limit(_size)
    bb
  }

  private def ensureAdditionalCapacity(count: Int, padding: Int) {
    val required = _size + count
    if (required > _array.length) {
      val pad = if (padding == 0) required else padding
      val newArray = new Array[Byte](required + pad)
      System.arraycopy(_array, 0, newArray, 0, _array.length)
      _array = newArray
    }
  }

}

object BufferBuilder {
  def apply(): BufferBuilder =
    new BufferBuilder(0, spray.util.EmptyByteArray)

  def apply(initialCapacity: Int): BufferBuilder =
    new BufferBuilder(0, new Array[Byte](initialCapacity))

  def apply(initialContent: Array[Byte]): BufferBuilder =
    new BufferBuilder(initialContent.length, initialContent)

  def apply(string: String): BufferBuilder =
    BufferBuilder(string.length).append(string)
}