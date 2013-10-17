/*
 * Copyright © 2013 the spray project <http://spray.io>
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

sealed trait LinkParam extends Renderable

object LinkDirective {
  def apply(uri: Uri, first: LinkParam, more: LinkParam*): LinkDirective = apply(uri, first +: more)
  implicit val paramsRenderer: Renderer[Seq[LinkParam]] =
    Renderer.seqRenderer(separator = "; ") // cache
}

case class LinkDirective(uri: Uri, params: Seq[LinkParam]) extends ValueRenderable {
  import LinkDirective.paramsRenderer

  def render[R <: Rendering](r: R): r.type =
    r ~~ '<' ~~ uri ~~ ">; " ~~ params
}

object LinkDirectives {

  // http://tools.ietf.org/html/rfc5988#section-5.2
  case class anchor(uri: Uri) extends LinkParam {
    def render[R <: Rendering](r: R): r.type =
      r ~~ productPrefix ~~ '=' ~~ '"' ~~ uri ~~ '"'
  }

  // http://tools.ietf.org/html/rfc5988#section-5.3
  // A rel can be either a bare word, an absolute URI,
  // or a quoted, space-separated string of zero-or-more of either.
  case class rel(value: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ productPrefix ~~ '='
      if (value.contains(' ') || value.contains(';') || value.contains(','))
        r ~~ '"' ~~ value ~~ '"'
      else
        r ~~ value
      r
    }
  }

  // A few convenience rels
  val next = rel("next")
  val prev = rel("prev")
  val first = rel("first")
  val last = rel("last")

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class title(title: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type =
      r ~~ productPrefix ~~ "=" ~~ '"' ~~ title ~~ '"'
  }

}