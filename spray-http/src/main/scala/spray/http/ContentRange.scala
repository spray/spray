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

sealed trait ContentRangeLike extends ValueRenderable {
  def instanceLength: Option[Long]
}

case class UnsatisfiableContentRange(instanceLength: Option[Long]) extends ContentRangeLike {
  override def render[R <: Rendering](r: R): r.type = {
    r ~~ "bytes */"
    if (instanceLength.isDefined)
      r ~~ instanceLength.get.toString
    else
      r ~~ '*'
  }
}

case class ContentRange(firstByte: Long, lastByte: Long, instanceLength: Option[Long]) extends ContentRangeLike {
  require(firstByte >= 0L, "firstByte must be non negative")
  require(firstByte <= lastByte, "firstByte must be <= lastByte")
  require(instanceLength.isEmpty || instanceLength.get > lastByte, "instanceLength must be empty or > lastByte")

  def render[R <: Rendering](r: R): r.type = {
    r ~~ "bytes " ~~ firstByte ~~ '-' ~~ lastByte ~~ '/'
    if (instanceLength.isDefined)
      r ~~ instanceLength.get.toString
    else
      r ~~ '*'
  }
}

