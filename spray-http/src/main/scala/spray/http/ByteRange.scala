/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.http

sealed trait ByteRange extends ValueRenderable

object ByteRange {
  def apply(first: Long, last: Long) = Slice(first, last)
  def fromOffset(offset: Long) = FromOffset(offset)
  def suffix(length: Long) = Suffix(length)

  case class Slice(first: Long, last: Long) extends ByteRange {
    require(0 <= first && first <= last, "first must be >= 0 and <= last")
    def render[R <: Rendering](r: R): r.type = r ~~ first ~~ '-' ~~ last
  }

  case class FromOffset(offset: Long) extends ByteRange {
    require(0 <= offset, "offset must be >= 0")
    def render[R <: Rendering](r: R): r.type = r ~~ offset ~~ '-'
  }

  case class Suffix(length: Long) extends ByteRange {
    require(0 <= length, "length must be >= 0")
    def render[R <: Rendering](r: R): r.type = r ~~ '-' ~~ length
  }
}