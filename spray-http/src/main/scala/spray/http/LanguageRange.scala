/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

sealed abstract class LanguageRange extends ValueRenderable with WithQValue[LanguageRange] {
  def qValue: Float
  def primaryTag: String
  def subTags: Seq[String]
  def matches(lang: Language): Boolean
  def render[R <: Rendering](r: R): r.type = {
    r ~~ primaryTag
    if (subTags.nonEmpty) subTags.foreach(r ~~ '-' ~~ _)
    if (qValue < 1.0f) r ~~ ";q=" ~~ qValue
    r
  }
}

object Language {
  def apply(primaryTag: String, subTags: String*) = new Language(primaryTag, subTags)
}
case class Language(primaryTag: String, subTags: Seq[String], qValue: Float = 1.0f) extends LanguageRange {
  def matches(lang: Language): Boolean = lang.primaryTag == this.primaryTag && lang.subTags == this.subTags
  def withQValue(qValue: Float) = Language(primaryTag, subTags, qValue)
}

object LanguageRanges {
  object `*` extends `*`(1.0f)

  case class `*`(qValue: Float) extends LanguageRange {
    def primaryTag = "*"
    def subTags = Nil
    def matches(lang: Language): Boolean = true
    def withQValue(qValue: Float) =
      if (qValue == 1.0f) `*` else if (qValue != this.qValue) `*`(qValue.toFloat) else this
  }
}