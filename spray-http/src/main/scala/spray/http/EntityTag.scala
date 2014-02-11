/*
 * Copyright © 2011-2014 the spray project <http://spray.io>
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

case class EntityTag(tag: String, weak: Boolean = false) extends ValueRenderable {
  def render[R <: Rendering](r: R): r.type = if (weak) r ~~ "W/" ~~#! tag else r ~~#! tag
}

sealed abstract class EntityTagRange extends ValueRenderable {
  def matches(entityTag: EntityTag, weak: Boolean): Boolean
}
object EntityTagRange {
  implicit val tagsRenderer = Renderer.defaultSeqRenderer[EntityTag] // cache
  case object `*` extends EntityTagRange {
    def render[R <: Rendering](r: R): r.type = r ~~ '*'
    def matches(entityTag: EntityTag, weak: Boolean) = weak || !entityTag.weak
  }
  case class Default(tags: Seq[EntityTag]) extends EntityTagRange {
    require(tags.nonEmpty, "tags must not be empty")
    def render[R <: Rendering](r: R): r.type = r ~~ tags
    def matches(entityTag: EntityTag, weak: Boolean) = tags.exists { t ⇒
      t.tag == entityTag.tag && (weak || !t.weak && !entityTag.weak)
    }
  }
  def apply(tags: Seq[EntityTag]) = Default(tags)
}
