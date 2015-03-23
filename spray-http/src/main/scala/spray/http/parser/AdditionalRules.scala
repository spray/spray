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
package parser

import org.parboiled.scala._
import BasicRules._

// implementation of additional parsing rules required for extensions that are not in the core HTTP standard
private[parser] trait AdditionalRules {
  this: Parser ⇒

  def Ip: Rule1[RemoteAddress] = rule {
    IPv4Address ~~> ((a, b, c, d) ⇒ RemoteAddress(Array(a, b, c, d)))
  }

  def Challenge = rule {
    Token ~ OptWS ~ zeroOrMore(AuthParam, separator = ListSep) ~~> { (scheme, params) ⇒
      val (realms, otherParams) = params.partition(_._1 equalsIgnoreCase "realm")
      HttpChallenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
    }
  }

  def AuthParam = rule {
    Token ~ "=" ~ (Token | QuotedString) ~~> ((_, _))
  }

  def originListOrNull: Rule1[Seq[HttpOrigin]] = rule {
    "null" ~ push(Nil: Seq[HttpOrigin]) |
      oneOrMore(origin)
  }

  def origin: Rule1[HttpOrigin] = rule {
    oneOrMore(!LWS ~ ANY) ~> (HttpOrigin(_)) // offload to URL parser
  }
}
