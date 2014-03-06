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

sealed trait RangeUnit extends ValueRenderable

object RangeUnit {
  object Bytes extends RangeUnit {
    def render[R <: Rendering](r: R): r.type = r ~~ "bytes"
  }

  case class Other(name: String) extends RangeUnit {
    def render[R <: Rendering](r: R): r.type = r ~~ name
  }
}

