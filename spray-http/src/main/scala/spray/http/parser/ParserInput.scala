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

package spray.http.parser

import java.nio.charset.Charset

sealed abstract class ParserInput {
  def charAt(ix: Int): Char
  def length: Int
  def sliceString(start: Int, end: Int): String
  override def toString: String = sliceString(0, length)
}

// bimorphic ParserInput implementation
// Note: make sure to not add another implementation, otherwise usage of this class
// might turn megamorphic at the call-sites thereby effectively disabling method inlining!
object ParserInput {
  implicit def apply(bytes: Array[Byte]): ParserInput = apply(bytes, spray.http.UTF8)
  def apply(bytes: Array[Byte], charset: Charset): ParserInput =
    new ParserInput {
      def charAt(ix: Int) = bytes(ix).toChar
      def length = bytes.length
      def sliceString(start: Int, end: Int) = new String(bytes, start, end - start, charset)
    }
  implicit def apply(chars: Array[Char]): ParserInput =
    new ParserInput {
      def charAt(ix: Int) = chars(ix)
      def length = chars.length
      def sliceString(start: Int, end: Int) = new String(chars, start, end - start)
    }

  private val field = classOf[String].getDeclaredField("value")
  field.setAccessible(true)

  implicit def apply(string: String): ParserInput = apply {
    // http://stackoverflow.com/questions/8894258/fastest-way-to-iterate-over-all-the-chars-in-a-string
    if (string.length > 64) field.get(string).asInstanceOf[Array[Char]]
    else string.toCharArray
  }
}