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

package spray
package http
package parser

import org.parboiled.scala._
import BasicRules._

private[parser] trait WwwAuthenticateHeader {
  this: Parser with AdditionalRules =>

  def WWW_AUTHENTICATE = rule {
    oneOrMore(Challenge, separator = ListSep) ~ EOI ~~> (HttpHeaders.`WWW-Authenticate`(_))
  }
  
  def Challenge = rule {
    AuthScheme ~ zeroOrMore(AuthParam, separator = ListSep) ~~> { (scheme, params) =>
      val (realms, otherParams) = params.partition(_._1 == "realm")
      HttpChallenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
    }
  }
  
}