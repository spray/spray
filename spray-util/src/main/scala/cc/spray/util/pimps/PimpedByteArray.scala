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

package spray.util
package pimps

import java.nio.charset.Charset
import annotation.tailrec


class PimpedByteArray(underlying: Array[Byte]) {

  /**
   * Creates a new Array[Byte] that is the concatenation of the underlying and the given one.
   */
  def concat(other: Array[Byte]) = {
    val newArray = new Array[Byte](underlying.length + other.length)
    System.arraycopy(underlying, 0, newArray, 0, underlying.length)
    System.arraycopy(other, 0, newArray, underlying.length, other.length)
    newArray
  }

  def asString: String = asString(UTF8)

  def asString(charset: Charset): String = new String(underlying, charset)

  def asString(charset: String): String = new String(underlying, charset)


  /**
   * Tests two byte arrays for value equality avoiding timing attacks.
   *
   * @note This function leaks information about the length of each byte array as well as
   *       whether the two byte arrays have the same length.
   * @see [[http://codahale.com/a-lesson-in-timing-attacks/]]
   * @see [[http://rdist.root.org/2009/05/28/timing-attack-in-google-keyczar-library/]]
   * @see [[http://emerose.com/timing-attacks-explained]]
   */
  def secure_==(other: Array[Byte]): Boolean = {
    @tailrec def xor(ix: Int = 0, result: Int = 0): Int =
      if (ix < underlying.length) xor(ix + 1, result | (underlying(ix) ^ other(ix))) else result

    other.length == underlying.length && xor() == 0
  }

}