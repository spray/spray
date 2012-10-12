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
  with CacheControlHeader
  with ContentEncodingHeader
  with ContentTypeHeader
  with CookieHeaders
  with SimpleHeaders
  with WwwAuthenticateHeader
  {

  // all string literals automatically receive a trailing optional whitespace
  override implicit def toRule(string :String): Rule0 =
    super.toRule(string) ~ BasicRules.OptWS

  val rules: Map[String, Rule1[HttpHeader]] =
    HttpParser
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .map { method =>
        method.getName.toLowerCase.replace('_', '-') -> method.invoke(HttpParser).asInstanceOf[Rule1[HttpHeader]]
      } (collection.breakOut)

  def parseHeader(header: HttpHeader): Either[String, HttpHeader] = {
    header match {
      case x@ HttpHeaders.RawHeader(name, value) =>
        rules.get(x.lowercaseName) match {
          case Some(rule) => parse(rule, value).left.map("Illegal HTTP header '" + name + "': " + _.formatPretty)
          case None => Right(x) // if we don't have a rule for the header we leave it unparsed
        }
      case x => Right(x) // already parsed
    }
  }

  def parseHeaders(headers: List[HttpHeader]): (List[String], List[HttpHeader]) = {
    val errors = List.newBuilder[String]
    val parsedHeaders = headers.map { header =>
      parseHeader(header) match {
        case Right(parsed) => parsed
        case Left(error) => errors += error; header
      }
    }
    (errors.result(), parsedHeaders)
  }

  def parseContentType(contentType: String): Either[RequestErrorInfo, ContentType] =
    parse(HttpParser.ContentTypeHeaderValue, contentType).left.map(_.withFallbackSummary("Illegal Content-Type"))
}