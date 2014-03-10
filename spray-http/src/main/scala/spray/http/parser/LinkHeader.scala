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

package spray
package http
package parser

import org.parboiled.scala._
import BasicRules._
import LinkDirectives._
import java.lang.{ StringBuilder ⇒ JStringBuilder }
import scala.annotation.tailrec

// http://tools.ietf.org/html/rfc5988#section-5
private[parser] trait LinkHeader {
  this: Parser with ProtocolParameterRules ⇒

  def `*Link` = rule {
    oneOrMore(`link-value`, separator = ListSep) ~ EOI ~~> (HttpHeaders.Link(_))
  }

  def `link-value` = rule {
    `URI-Reference-Between-Triangles` ~ OptWS ~ oneOrMore(`link-param`) ~~>
      ((uri, params) ⇒ LinkDirective(uri, ensureOnlyOneLinkParam(params)))
  }

  def `URI-Reference-Between-Triangles` = rule {
    "<" ~ push(new JStringBuilder) ~ oneOrMore(QDText(ch('>'))) ~ ">" ~~> (x ⇒ Uri(x.toString))
  }

  def `URI-Reference-Quoted` = rule {
    "\"" ~ push(new JStringBuilder) ~ oneOrMore(QDText(ch('"'))) ~ "\"" ~~> (x ⇒ Uri(x.toString))
  }

  def `relation-types` = rule(
    `relation-type` ~> (x ⇒ x.mkString)
      | "\"" ~ oneOrMore(`relation-type`, separator = " ") ~> (x ⇒ x.mkString) ~ "\"")

  def `relation-type` = rule(
    `ext-rel-type`
      | `reg-rel-type`)

  def `reg-rel-type` = rule {
    LoAlpha ~ zeroOrMore(LoAlpha | Digit | "." | "-")
  }

  def `ext-rel-type` = rule(oneOrMore(`URI-Char`))

  def `URI-Char` = rule(noneOf(";, \"")) // TODO this is too simplified

  def `link-param` = rule(
    OptWS ~ ";" ~ OptWS ~ (
      "rel=" ~ `relation-types` ~~> (rel(_))
      | "anchor=" ~ `URI-Reference-Quoted` ~~> (anchor(_))
      | "title=" ~ QuotedString ~~> (title(_))))

  /** Skips `rel` params after the first, see http://tools.ietf.org/html/rfc5988#section-5.3 */
  def ensureOnlyOneLinkParam(l: List[LinkParam]): List[LinkParam] = {
    @tailrec def rec(remaining: List[LinkParam], res: List[LinkParam] = Nil, seenRel: Boolean = false): List[LinkParam] =
      remaining match {
        case (_: rel) :: rest if seenRel        ⇒ rec(rest, res, seenRel)
        case (r: rel) :: rest /* if !seenRel */ ⇒ rec(rest, r :: res, seenRel = true)
        case first :: rest                      ⇒ rec(rest, first :: res, seenRel)
        case Nil                                ⇒ res
      }

    rec(l).reverse
  }
}
