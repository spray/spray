/*
 * Copyright © 2015 the spray project <http://spray.io>
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

import spray.http.parser.CharPredicate

trait LinkHeaderCompanion {
  sealed trait Param extends Renderable

  case class Value(uri: Uri, params: Seq[Param]) extends ValueRenderable {
    import Value.paramsRenderer
    def render[R <: Rendering](r: R): r.type = r ~~ '<' ~~ uri ~~ ">; " ~~ params
  }
  object Value {
    def apply(uri: Uri, first: Param, more: Param*): Value = apply(uri, first +: more)
    implicit val paramsRenderer: Renderer[Seq[Param]] = Renderer.seqRenderer(separator = "; ")
  }

  private val reserved = CharPredicate(" ,;")

  // A few convenience rels
  val next = rel("next")
  val prev = rel("prev")
  val first = rel("first")
  val last = rel("last")

  // http://tools.ietf.org/html/rfc5988#section-5.3
  // Can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either.
  case class rel(value: String) extends Param {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rel="
      if (reserved.matchAny(value)) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.2
  case class anchor(uri: Uri) extends Param {
    def render[R <: Rendering](r: R): r.type = r ~~ "anchor=\"" ~~ uri ~~ '"'
  }

  // http://tools.ietf.org/html/rfc5988#section-5.3
  // Can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either.
  case class rev(value: String) extends Param {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rev="
      if (reserved.matchAny(value)) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class hreflang(lang: Language) extends Param {
    def render[R <: Rendering](r: R): r.type = r ~~ "hreflang=" ~~ lang
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class media(desc: String) extends Param {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "media="
      if (reserved.matchAny(desc)) r ~~ '"' ~~ desc ~~ '"' else r ~~ desc
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class title(title: String) extends Param {
    def render[R <: Rendering](r: R): r.type = r ~~ "title=\"" ~~ title ~~ '"'
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class `title*`(title: String) extends Param {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "title*="
      if (reserved.matchAny(title)) r ~~ '"' ~~ title ~~ '"' else r ~~ title
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class `type`(mediaType: MediaType) extends Param {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "type="
      if (reserved.matchAny(mediaType.value)) r ~~ '"' ~~ mediaType.value ~~ '"' else r ~~ mediaType.value
    }
  }
}