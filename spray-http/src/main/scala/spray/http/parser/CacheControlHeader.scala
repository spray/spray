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

package spray
package http
package parser

import org.parboiled.scala._
import BasicRules._
import CacheDirectives._
import HttpHeaders._

private[parser] trait CacheControlHeader {
  this: Parser with ProtocolParameterRules ⇒

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9
  def `*Cache-Control` = rule(zeroOrMore(cacheDirective, separator = ListSep) ~ EOI ~~> (`Cache-Control`(_)))

  def cacheDirective = rule(
    "no-cache" ~ push(`no-cache`)
      | "no-store" ~ push(`no-store`)
      | "no-transform" ~ push(`no-transform`)
      | "max-age=" ~ DeltaSeconds ~~> (`max-age`(_))

      | "max-stale" ~ optional("=" ~ DeltaSeconds) ~~> (`max-stale`(_))
      | "min-fresh=" ~ DeltaSeconds ~~> (`min-fresh`(_))
      | "only-if-cached" ~ push(`only-if-cached`)

      | "public" ~ push(`public`)
      | "private" ~ optional("=" ~ FieldNames) ~~> (fn ⇒ `private`((fn getOrElse Nil): _*))
      | "no-cache" ~ optional("=" ~ FieldNames) ~~> (fn ⇒ `no-cache`((fn getOrElse Nil): _*))
      | "must-revalidate" ~ push(`must-revalidate`)
      | "proxy-revalidate" ~ push(`proxy-revalidate`)
      | "s-maxage=" ~ DeltaSeconds ~~> (`s-maxage`(_))

      | Token ~ optional("=" ~ (Token | QuotedString)) ~~> (CacheDirective.custom(_, _)))

  def FieldNames = rule { oneOrMore(QuotedString, separator = ListSep) }

  def ETagDef = rule { EntityTag ~~> ((weak, tag) ⇒ http.EntityTag(tag, weak)) }

  def OpaqueTagDef = rule { OpaqueTag ~~> (http.EntityTag(_, weak = false)) }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.19
  def `*ETag` = rule { ETagDef ~ EOI ~~> (ETag(_)) }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24
  def `*If-Match` = rule {
    "*" ~ push(`If-Match`.`*`) |
      oneOrMore(OpaqueTagDef, separator = ListSep) ~ EOI ~~> (tags ⇒ `If-Match`(EntityTagRange(tags)))
  }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
  def `*If-Modified-Since` = rule { HttpDate ~ EOI ~~> (`If-Modified-Since`(_)) }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26
  def `*If-None-Match` = rule {
    "*" ~ push(`If-None-Match`.`*`) |
      oneOrMore(ETagDef, separator = ListSep) ~ EOI ~~> (tags ⇒ `If-None-Match`(EntityTagRange(tags)))
  }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28
  def `*If-Unmodified-Since` = rule { HttpDate ~ EOI ~~> (`If-Unmodified-Since`(_)) }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.29
  def `*Last-Modified` = rule { HttpDate ~ EOI ~~> (`Last-Modified`(_)) }
}