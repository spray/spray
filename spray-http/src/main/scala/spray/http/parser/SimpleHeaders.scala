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
package parser

import org.parboiled.scala._
import spray.util.identityFunc
import BasicRules._
import HttpHeaders._
import ProtectedHeaderCreation.enable

/**
 * parser rules for all headers that can be parsed with one simple rule
 */
private[parser] trait SimpleHeaders {
  this: Parser with ProtocolParameterRules with AdditionalRules ⇒

  def `*Allow` = rule(
    zeroOrMore(HttpParser.HttpMethodDef, separator = ListSep) ~ EOI ~~> (Allow(_: _*)))

  def `*Connection` = rule(
    oneOrMore(Token, separator = ListSep) ~ EOI ~~> (HttpHeaders.Connection(_)))

  def `*Content-Length` = rule {
    oneOrMore(Digit) ~> (s ⇒ `Content-Length`(s.toLong)) ~ EOI
  }

  def `*Content-Disposition` = rule {
    Token ~ zeroOrMore(";" ~ Parameter) ~ EOI ~~> (_.toMap) ~~> (`Content-Disposition`(_, _))
  }

  def `*Date` = rule {
    HttpDate ~ EOI ~~> (Date(_))
  }

  def `*Expect` = rule(
    oneOrMore(Token ~ &(EOI) | Token ~ "=" ~ (Token | QuotedString) ~~> (_ + '=' + _), separator = ListSep) ~ EOI
      ~~> (Expect(_)))

  // We don't accept scoped IPv6 addresses as they should not appear in the Host header,
  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  // Also: an empty hostnames with a non-empty port value (as in `Host: :8080`) are *allowed*,
  // see http://trac.tools.ietf.org/wg/httpbis/trac/ticket/92
  def `*Host` = rule(
    (Token | IPv6Reference | push("")) ~ OptWS ~ optional(":" ~ oneOrMore(Digit) ~> (_.toInt)) ~ EOI
      ~~> ((h, p) ⇒ Host(h, p.getOrElse(0))))

  def `*Location` = rule {
    oneOrMore(Text) ~> { uri ⇒ Location(Uri(uri)) } ~ EOI
  }

  def `*Proxy-Authenticate` = rule {
    oneOrMore(Challenge, separator = ListSep) ~ EOI ~~> (HttpHeaders.`Proxy-Authenticate`(_))
  }

  def `*Remote-Address` = rule {
    Ip ~ EOI ~~> (`Remote-Address`(_))
  }

  def `*Server` = rule { ProductVersionComments ~~> (Server(_)) }

  def `*Transfer-Encoding` = rule {
    oneOrMore(TransferCoding ~> identityFunc, separator = ListSep) ~ EOI ~~> (`Transfer-Encoding`(_))
  }

  def `*User-Agent` = rule { ProductVersionComments ~~> (`User-Agent`(_)) }

  def `*WWW-Authenticate` = rule {
    oneOrMore(Challenge, separator = ListSep) ~ EOI ~~> (HttpHeaders.`WWW-Authenticate`(_))
  }

  // de-facto standard as per http://en.wikipedia.org/w/index.php?title=X-Forwarded-For&oldid=563040890
  // It's not clear in which format IpV6 addresses are to be expected, the ones we've seen in the wild
  // were not quoted and that's also what the "Transition" section in the draft says:
  // http://tools.ietf.org/html/draft-ietf-appsawg-http-forwarded-10
  def `*X-Forwarded-For` = rule {
    oneOrMore(Ip | IPv6Address ~~> (RemoteAddress(_)) | "unknown" ~ push(RemoteAddress.Unknown), separator = ListSep) ~ EOI ~~>
      (`X-Forwarded-For`(_))
  }
}
