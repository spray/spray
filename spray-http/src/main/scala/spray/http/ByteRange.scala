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

sealed trait ByteRangeSetEntry extends ValueRenderable

object ByteRange {
  def apply(firstBytePosition: Long, lastBytePosition: Long): ByteRange = ByteRange(firstBytePosition, Some(lastBytePosition))
}

case class ByteRange(firstBytePosition: Long, lastBytePosition: Option[Long] = None) extends ByteRangeSetEntry {
  require(firstBytePosition <= lastBytePosition.getOrElse(Long.MaxValue), "firstBytePosition must be <= lastBytePosition")

  def render[R <: Rendering](r: R): r.type = r ~~ firstBytePosition.toString ~~ '-' ~~ {
    lastBytePosition match {
      case Some(x) ⇒ x.toString
      case _       ⇒ ""
    }
  }
}

case class SuffixByteRange(suffixLength: Long) extends ByteRangeSetEntry {
  def render[R <: Rendering](r: R): r.type = r ~~ '-' ~~ suffixLength.toString
}

case class MultipartByteRanges(parts: Seq[ByteRangePart])

object MultipartByteRanges {
  val Empty = MultipartByteRanges(Nil)
}

/**
 * Model for one part of a multipart/byteranges message.
 */
case class ByteRangePart(entity: HttpEntity, headers: Seq[HttpHeader] = Nil) {

  def contentRange: Option[ContentRangeLike] =
    headers.collectFirst {
      case contentRangeHeader: HttpHeaders.`Content-Range` ⇒ contentRangeHeader.contentRange
    }

}

