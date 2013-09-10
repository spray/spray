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
import BasicRules._
import HttpHeaders._
import HttpMethods._
import ProtectedHeaderCreation.enable
import org.parboiled.errors.ParsingException

/**
 * parser rules for CORS header that does not fit to SimpleHeaders
 * Spec http://www.w3.org/TR/cors/
 */
private[parser] trait CORSHeaders {
  this: Parser with ProtocolParameterRules with AdditionalRules ⇒

  def `*Access-Control-Allow-Methods` = rule {
    oneOrMore(HttpMethodDef, separator = ListSep) ~ EOI ~~> (`Access-Control-Allow-Methods`(_))
  }

  def `*Access-Control-Allow-Headers` = rule {
    oneOrMore(Token, separator = ListSep) ~ EOI ~~> (`Access-Control-Allow-Headers`(_))
  }

  def `*Access-Control-Request-Method` = rule {
    HttpMethodDef ~ EOI ~~> (`Access-Control-Request-Method`(_))
  }

  def `*Access-Control-Request-Headers` = rule {
    oneOrMore(Token, separator = ListSep) ~ EOI ~~> (`Access-Control-Request-Headers`(_))
  }

  def `*Access-Control-Allow-Origin` = rule {
    oneOrMore(Text) ~> (`Access-Control-Allow-Origin`(_)) ~ EOI
  }

  def `*Access-Control-Expose-Headers` = rule {
    oneOrMore(Token, separator = ListSep) ~ EOI ~~> (`Access-Control-Expose-Headers`(_))
  }

  def `*Access-Control-Max-Age` = rule {
    oneOrMore(Digit) ~> (s ⇒ `Access-Control-Max-Age`(s.toLong)) ~ EOI
  }

  //according to http://www.w3.org/TR/cors/#access-control-allow-credentials-response-header this is case-sensitive
  def `*Access-Control-Allow-Credentials` = rule {
    ("true" ~ push(`Access-Control-Allow-Credentials`(true)) | "false" ~ push(`Access-Control-Allow-Credentials`(false))) ~ EOI
  }

  def `*Origin` = rule {
    oneOrMore(Text) ~> { uri ⇒ Origin(Uri.parseAbsolute(uri)) } ~ EOI
  }

  def HttpMethodDef = rule {
    Token ~~> { s ⇒
      HttpMethods.getForKey(s) match {
        case Some(m) ⇒ m
        case None    ⇒ throw new ParsingException("Unknown HTTP method: " + s)
      }
    }
  }

}