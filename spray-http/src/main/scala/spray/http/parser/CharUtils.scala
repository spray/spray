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

object CharUtils {
  def hexValue(c: Char): Int = (c & 0x1f) + ((c >> 6) * 0x19) - 0x10

  def toLowerCase(c: Char): Char = if (CharPredicate.UpperAlpha(c)) (c + 0x20).toChar else c

  def abs(i: Int): Int = { val j = i >> 31; (i ^ j) - j }

  def escape(c: Char): String = c match {
    case '\t'                           ⇒ "\\t"
    case '\r'                           ⇒ "\\r"
    case '\n'                           ⇒ "\\n"
    case x if Character.isISOControl(x) ⇒ "\\u%04x" format c.toInt
    case x                              ⇒ x.toString
  }
}