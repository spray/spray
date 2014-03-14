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

sealed trait ContentRange extends ValueRenderable {
  def instanceLength: Option[Long]
}

// http://tools.ietf.org/html/rfc2616#section-14.16
object ContentRange {
  def apply(first: Long, last: Long): Default = apply(first, last, None)
  def apply(first: Long, last: Long, instanceLength: Long): Default = apply(first, last, Some(instanceLength))
  def apply(first: Long, last: Long, instanceLength: Option[Long]): Default = Default(first, last, instanceLength)

  /**
   * Models a satisfiable HTTP content-range.
   */
  case class Default(first: Long, last: Long, instanceLength: Option[Long]) extends ContentRange {
    require(0 <= first && first <= last, "first must be >= 0 and <= last")
    require(instanceLength.isEmpty || instanceLength.get > last, "instanceLength must be empty or > last")

    def render[R <: Rendering](r: R): r.type = {
      r ~~ first ~~ '-' ~~ last ~~ '/'
      if (instanceLength.isDefined) r ~~ instanceLength.get else r ~~ '*'
    }
  }

  /**
   * An unsatisfiable content-range.
   */
  case class Unsatisfiable(instanceLength: Option[Long]) extends ContentRange {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "*/"
      if (instanceLength.isDefined) r ~~ instanceLength.get else r ~~ '*'
    }
  }
  object Unsatisfiable extends Unsatisfiable(None) {
    def apply(instanceLength: Long): Unsatisfiable = apply(Some(instanceLength))
  }
}