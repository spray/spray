/*
 * Copyright (C) 2011-2012 spray.io
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
import BasicRules._

// http://tools.ietf.org/html/draft-ietf-httpstate-cookie-23#section-4
// with one exception: we are more lenient on additional or missing whitespace
private[parser] trait CookieHeaders {
  this: Parser with ProtocolParameterRules =>

  def SET_COOKIE = rule {
    CookiePair ~ zeroOrMore(";" ~ CookieAttrs) ~ EOI ~~> (HttpHeaders.`Set-Cookie`(_))
  }

  def COOKIE = rule {
    oneOrMore(CookiePair, separator = ";") ~ EOI ~~> (HttpHeaders.`Cookie`(_))
  }
  
  def CookiePair = rule {
    Token ~ ch('=') ~ CookieValue ~~> (HttpCookie(_, _))
  }

  def CookieValue = rule (
      ch('"') ~ zeroOrMore(CookieOctet) ~> identityFunc ~ "\""
    | zeroOrMore(CookieOctet) ~> identityFunc ~ OptWS
  )

  def CookieOctet = rule {
    ch('\u0021') | ch('\u0023') - "\u002b" | ch('\u002d') - "\u003a" | ch('\u003c') - "\u005b" | ch('\u005d') - "\u007e"
  }

  def CookieAttrs = rule (
      str("Expires=") ~ HttpDate ~~> { (cookie: HttpCookie, dateTime: DateTime) => cookie.copy(expires = Some(dateTime)) }
    | str("Max-Age=") ~ NonNegativeLong ~~> { (cookie: HttpCookie, seconds: Long) => cookie.copy(maxAge = Some(seconds)) }
    | str("Domain=") ~ DomainName ~~> { (cookie: HttpCookie, domainName: String) => cookie.copy(domain = Some(domainName)) }
    | str("Path=") ~ StringValue ~~> { (cookie: HttpCookie, pathValue: String) => cookie.copy(path = Some(pathValue)) }
    | str("Secure") ~~> { (cookie: HttpCookie) => cookie.copy(secure = true) }
    | str("HttpOnly") ~~> { (cookie: HttpCookie) => cookie.copy(httpOnly = true) }
    | StringValue ~~> { (cookie: HttpCookie, stringValue: String) => cookie.copy(extension = Some(stringValue)) }
  )

  def NonNegativeLong = rule { oneOrMore(Digit) ~> (_.toLong) }

  def DomainName = rule { oneOrMore(DomainNamePart, separator = ch('.')) ~> identityFunc }

  def DomainNamePart = rule { AlphaNum ~ zeroOrMore(AlphaNum | ch('-')) }

  def StringValue = rule { oneOrMore(!(CTL | ch(';')) ~ Char) ~> identityFunc }
}