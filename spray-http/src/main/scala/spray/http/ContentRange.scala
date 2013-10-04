/*
 * Copyright (C) 2011-2013 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

case class ContentRange(firstByte: Option[Long], lastByte: Option[Long], instanceLength: Option[Long]) extends ValueRenderable {

  /*require(firstByte.getOrElse(0L) >= 0L, s"firstByte must be non negative")
  require(lastByte.getOrElse(0L) >= 0L, s"lastByte must be non negative")
  require((firstByte.isDefined && lastByte.isDefined) || (firstByte.isEmpty && lastByte.isEmpty), s"firstByte and lastByte must both be given or not given")
  require(firstByte.getOrElse(0L) <= lastByte.getOrElse(Long.MaxValue), s"firstByte must be less than lastByte")
  require(instanceLength.getOrElse(0L) >= 0, s"instanceLength must be non negative")
  require(instanceLength.getOrElse(Long.MaxValue) > lastByte.getOrElse(0L), s"instanceLength must be greater than lastByte")
  */

  def render[R <: Rendering](r: R): r.type = {
    r ~~ "bytes "
    if (firstByte.isEmpty || lastByte.isEmpty) {
      r ~~ "*"
    } else {
      r ~~ firstByte.get ~~ '-' ~~ lastByte.get
    }
    if (instanceLength.isDefined)
      r ~~ '/' ~~ instanceLength.get.toString
    else
      r ~~ "/*"
    r
  }
}

object ContentRange {

  def unsatisfiable(instanceLength: Long): ContentRange = ContentRange(None, None, Some(instanceLength))
  def apply(firstByte: Long, lastByte: Long, instanceLength: Long): ContentRange = ContentRange(Some(firstByte), Some(lastByte), Some(instanceLength))
  def apply(firstByte: Long, lastByte: Long): ContentRange = ContentRange(Some(firstByte), Some(lastByte), None)

}
