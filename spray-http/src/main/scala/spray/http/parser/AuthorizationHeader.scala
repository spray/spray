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
import org.parboiled.common.Base64
import spray.util.identityFunc
import BasicRules._

private[parser] trait AuthorizationHeader {
  this: Parser with ProtocolParameterRules with AdditionalRules ⇒

  def `*Authorization` = rule {
    CredentialDef ~ EOI ~~> (HttpHeaders.`Authorization`(_))
  }

  def `*Proxy-Authorization` = rule {
    CredentialDef ~ EOI ~~> (HttpHeaders.`Proxy-Authorization`(_))
  }

  def CredentialDef = rule {
    BasicCredentialDef | OAuth2BearerTokenDef | GenericHttpCredentialsDef
  }

  def BasicCredentialDef = rule {
    "Basic" ~ BasicCookie ~> (BasicHttpCredentials(_))
  }

  def BasicCookie = rule {
    oneOrMore(anyOf(Base64.rfc2045.getAlphabet)) ~ optional("==" | ch('='))
  }

  def OAuth2BearerTokenDef = rule {
    "Bearer" ~ Token ~~> (OAuth2BearerToken(_))
  }

  def GenericHttpCredentialsDef = rule(
    AuthScheme ~ (
      CredentialParams ~~> ((scheme: String, params) ⇒ GenericHttpCredentials(scheme, params))
      | RelaxedToken ~ (
        CredentialParams ~~> ((scheme: String, token: String, params) ⇒ GenericHttpCredentials(scheme, token, params))
        | EMPTY ~~> ((scheme: String, token: String) ⇒ GenericHttpCredentials(scheme, token)))
        | EMPTY ~~> ((scheme: String) ⇒ GenericHttpCredentials(scheme, ""))))

  def CredentialParams = rule { oneOrMore(AuthParam, separator = ListSep) ~~> (_.toMap) }

  def RelaxedToken = rule { oneOrMore(!CTL ~ !ch(' ') ~ !ch('"') ~ ANY) ~> identityFunc }
}