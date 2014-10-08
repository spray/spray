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

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import scala.annotation.tailrec
import org.parboiled.scala._
import HttpHeaders.Link

// http://tools.ietf.org/html/rfc5988#section-5
private[parser] trait LinkHeader {
  this: Parser with ProtocolParameterRules with CommonActions ⇒
  import BasicRules._

  def `*Link` = rule { oneOrMore(`link-value`, separator = ListSep) ~ EOI ~~> (Link(_)) }

  def `link-value` = rule(
    UriReference('<', '>') ~ oneOrMore(";" ~ `link-param`) ~~> ((uri, params) ⇒ Link.Value(uri, sanitize(params))))

  def `link-param` = rule(
    "rel" ~ "=" ~ `relation-types` ~~> Link.rel
      | "anchor" ~ "=" ~ UriReferencePotentiallyQuoted ~~> Link.anchor
      | "rev" ~ "=" ~ `relation-types` ~~> Link.rev
      | "hreflang" ~ "=" ~ LanguageTag ~~> Link.hreflang
      | "media" ~ "=" ~ (QuotedString | UnquotedString) ~~> Link.media
      | "title" ~ "=" ~ QuotedString ~~> Link.title
      | "title*" ~ "=" ~ (QuotedString | UnquotedString) ~~> Link.`title*`
      | "type" ~ "=" ~ (ch('"') ~ LinkMediaType ~ ch('"') | LinkMediaType) ~~> Link.`type`)

  def `relation-types` = rule(
    ch('"') ~ oneOrMore(`relation-type`, separator = " ") ~~> (_.mkString(" ")) ~ ch('"')
      | `relation-type`)

  def `relation-type` = rule(`ext-rel-type` | `reg-rel-type`)

  def `reg-rel-type` = rule { group(LoAlpha ~ zeroOrMore(LoAlpha | Digit | "." | "-")) ~> (identity) }

  def `ext-rel-type` = URI

  def URI = rule { oneOrMore(!(CTL | anyOf(" ,;\"")) ~ ANY) ~> (identity) } // TODO: attach actual URI parser

  def UnquotedString = rule { oneOrMore(!(CTL | anyOf(" ,;")) ~ ANY) ~> (identity) }

  def UriReference(leftDelimiter: Char, rightDelimiter: Char) =
    (ch(leftDelimiter) ~ push(new JStringBuilder) ~ oneOrMore(QDText(ch(rightDelimiter))) ~ ch(rightDelimiter) ~ OptWS
      ~~> (x ⇒ Uri(x.toString)))

  def UriReferencePotentiallyQuoted = rule(
    UriReference('"', '"') | push(new JStringBuilder) ~ oneOrMore(QDText(anyOf(" ,;"))) ~ OptWS
      ~~> (x ⇒ Uri(x.toString)))

  def LinkMediaType = rule { MediaTypeDef ~~> ((mt, st, pm) ⇒ getMediaType(mt, st, pm.toMap)) }

  // filter out subsequent `rel`, `media`, `title`, `type` and `type*` params
  @tailrec private def sanitize(params: List[Link.Param], result: List[Link.Param] = Nil, seenRel: Boolean = false,
                                seenMedia: Boolean = false, seenTitle: Boolean = false, seenTitleS: Boolean = false,
                                seenType: Boolean = false): List[Link.Param] =
    params match {
      case (x: Link.rel) :: tail      ⇒ sanitize(tail, if (seenRel) result else x :: result, seenRel = true, seenMedia, seenTitle, seenTitleS, seenType)
      case (x: Link.media) :: tail    ⇒ sanitize(tail, if (seenMedia) result else x :: result, seenRel, seenMedia = true, seenTitle, seenTitleS, seenType)
      case (x: Link.title) :: tail    ⇒ sanitize(tail, if (seenTitle) result else x :: result, seenRel, seenMedia, seenTitle = true, seenTitleS, seenType)
      case (x: Link.`title*`) :: tail ⇒ sanitize(tail, if (seenTitleS) result else x :: result, seenRel, seenMedia, seenTitle, seenTitleS = true, seenType)
      case (x: Link.`type`) :: tail   ⇒ sanitize(tail, if (seenType) result else x :: result, seenRel, seenMedia, seenTitle, seenTitleS, seenType = true)
      case head :: tail               ⇒ sanitize(tail, head :: result, seenRel, seenMedia, seenTitle, seenTitleS, seenType)
      case Nil                        ⇒ result.reverse
    }
}
