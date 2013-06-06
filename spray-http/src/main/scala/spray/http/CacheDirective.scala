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

import scala.annotation.tailrec

sealed trait CacheDirective extends Renderable {
  def value: String
}

object CacheDirective {
  sealed trait RequestDirective extends CacheDirective
  sealed trait ResponseDirective extends CacheDirective

  private case class CustomCacheDirective(name: String, content: Option[String])
      extends RequestDirective with ResponseDirective with ValueRenderable {
    def render[R <: Rendering](r: R): R = content match {
      case Some(s) ⇒ r ~~ name ~~ '=' ~~# s
      case None    ⇒ r ~~ name
    }
  }

  def custom(name: String, content: Option[String]): RequestDirective with ResponseDirective =
    CustomCacheDirective(name, content)
}

object CacheDirectives {
  import CacheDirective._

  /* Requests and Responses */
  case object `no-cache` extends SingletonValueRenderable with RequestDirective with ResponseDirective
  case object `no-store` extends SingletonValueRenderable with RequestDirective with ResponseDirective
  case object `no-transform` extends SingletonValueRenderable with RequestDirective with ResponseDirective

  case class `max-age`(deltaSeconds: Long) extends RequestDirective with ResponseDirective with ValueRenderable {
    def render[R <: Rendering](r: R): R = r ~~ productPrefix ~~ '=' ~~ deltaSeconds
  }

  /* Requests only */
  case class `max-stale`(deltaSeconds: Option[Long]) extends RequestDirective with ValueRenderable {
    def render[R <: Rendering](r: R): R = deltaSeconds match {
      case Some(s) ⇒ r ~~ productPrefix ~~ '=' ~~ s
      case None    ⇒ r ~~ productPrefix
    }
  }
  case class `min-fresh`(deltaSeconds: Long) extends RequestDirective with ValueRenderable {
    def render[R <: Rendering](r: R): R = r ~~ productPrefix ~~ '=' ~~ deltaSeconds
  }
  case object `only-if-cached` extends SingletonValueRenderable with RequestDirective

  /* Responses only */
  case object `public` extends SingletonValueRenderable with ResponseDirective

  abstract class FieldNamesDirective extends Product with ValueRenderable {
    def fieldNames: Seq[String]
    def render[R <: Rendering](r: R): R =
      if (fieldNames.nonEmpty) {
        r ~~ productPrefix ~~ '=' ~~ '"'
        @tailrec def rec(i: Int = 0): R =
          if (i < fieldNames.length) {
            if (i > 0) r ~~ ','
            r.putEscaped(fieldNames(i))
            rec(i + 1)
          } else r ~~ '"'
        rec()
      } else r ~~ productPrefix
  }
  case class `private`(fieldNames: String*) extends FieldNamesDirective with ResponseDirective
  case class `no-cache`(fieldNames: String*) extends FieldNamesDirective with ResponseDirective
  case object `must-revalidate` extends SingletonValueRenderable with ResponseDirective
  case object `proxy-revalidate` extends SingletonValueRenderable with ResponseDirective
  case class `s-maxage`(deltaSeconds: Long) extends ResponseDirective with ValueRenderable {
    def render[R <: Rendering](r: R): R = r ~~ productPrefix ~~ '=' ~~ deltaSeconds
  }
}
