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


import scala.annotation.tailrec

class PimpedString(underlying: String) {

  /**
   * Splits the underlying string into the segments that are delimited by the given character.
   * The delimiter itself is never a part of any segment. If the string does not contain the
   * delimiter the result is a List containing only the underlying string.
   * Note that this implementation differs from the original String.split(...) method in that
   * leading and trailing delimiters are NOT ignored, i.e. they trigger the inclusion of an
   * empty leading or trailing empty string (respectively).
   */
  def fastSplit(delimiter: Char): List[String] = {
    @tailrec
    def split(end: Int, elements: List[String]): List[String] = {
      val ix = underlying.lastIndexOf(delimiter, end - 1)
      if (ix < 0)
        underlying.substring(0, end) :: elements
      else
        split(ix, underlying.substring(ix + 1, end) :: elements)
    }
    split(underlying.length, Nil)
  }

  /**
   * Lazily splits the underlying string into the segments that are delimited by the given character.
   * Only the segments that are actually accessed are computed.
   * The delimiter itself is never a part of any segment. If the string does not contain the
   * delimiter the result is a single-element stream containing only the underlying string.
   * Note that this implementation differs from the original String.split(...) method in that
   * leading and trailing delimiters are NOT ignored, i.e. they trigger the inclusion of an
   * empty leading or trailing empty string (respectively).
   */
  def lazySplit(delimiter: Char): Stream[String] = {
    // based on an implemented by Jed Wesley-Smith
    def split(start: Int): Stream[String] = {
      val ix = underlying.indexOf(delimiter, start)
      if (ix < 0)
        Stream.cons(underlying.substring(start), Stream.Empty)
      else
        Stream.cons(underlying.substring(start, ix), split(ix + 1))
    }
    split(0)
  }

  /**
   * Returns Some(String) if the underlying string is non-emtpy, None otherwise
   */
  def toOption: Option[String] =
    if (underlying.isEmpty) None else Some(underlying)

  /**
   * If the underlying string is null the method returns the empty string, otherwise the underlying string.
   */
  def nullAsEmpty: String =
    if (underlying == null) "" else underlying

  /**
   * Returns the ASCII encoded bytes of this string.
   */
  def getAsciiBytes = {
    val sl = underlying.length
    val array = new Array[Byte](sl)
    var i = 0
    while (i < sl) {
      array(i) = underlying.charAt(i).asInstanceOf[Byte]
      i += 1
    }
    array
  }

  /**
   * Tests two strings for value equality avoiding timing attacks.
   * Note that this function still leaks information about the length of each string as well as
   * whether the two strings have the same length.
   */
  def secure_== (other: String): Boolean = getAsciiBytes secure_== other.getAsciiBytes

}