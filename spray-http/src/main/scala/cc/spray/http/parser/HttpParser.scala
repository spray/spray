/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.http
package parser

import org.parboiled.scala._

/**
 * Parser for all HTTP headers as defined by
 *  [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html]]
 */
object HttpParser extends SprayParser with ProtocolParameterRules with AdditionalRules with CommonActions
  with AcceptCharsetHeader
  with AcceptEncodingHeader
  with AcceptHeader
  with AcceptLanguageHeader
  with AcceptRangesHeader
  with AuthorizationHeader
  with ConnectionHeader
  with ContentEncodingHeader
  with ContentTypeHeader
  with CookieHeaders
  with SimpleHeaders
  with WwwAuthenticateHeader
  {

  // all string literals automatically receive a trailing optional whitespace
  override implicit def toRule(string :String) : Rule0 = {
    super.toRule(string) ~ BasicRules.OptWS
  }

  lazy val rules: Map[String, Rule1[HttpHeader]] = {
    // cache all the rules for public rule methods that have no lower-case letter in their name
    // (which are all the header rules)
    HttpParser.getClass.getMethods.filter(_.getName.forall(!_.isLower)).map { method =>
      method.getName -> method.invoke(HttpParser).asInstanceOf[Rule1[HttpHeader]]
    } (collection.breakOut)
  }

}