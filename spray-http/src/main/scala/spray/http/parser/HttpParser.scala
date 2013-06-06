/*
 * Copyright (C) 2011-2013 spray.io
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

import java.lang.reflect.Method
import scala.annotation.tailrec
import org.parboiled.scala._
import org.parboiled.errors.{ ParsingException, ParserRuntimeException, ErrorUtils }

/**
 * Parser for all HTTP headers as defined by
 *  [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html]]
 */
object HttpParser extends Parser with ProtocolParameterRules with AdditionalRules with CommonActions
    with AcceptCharsetHeader
    with AcceptEncodingHeader
    with AcceptHeader
    with AcceptLanguageHeader
    with AuthorizationHeader
    with CacheControlHeader
    with ContentEncodingHeader
    with ContentTypeHeader
    with CookieHeaders
    with SimpleHeaders
    with WwwAuthenticateHeader {

  // all string literals automatically receive a trailing optional whitespace
  override def toRule(string: String): Rule0 =
    super.toRule(string) ~ BasicRules.OptWS

  // seq of pretty header names and map of the *lowercase* header names to the respective parser rule
  val (headerNames, parserRules): (Seq[String], Map[String, Rule1[HttpHeader]]) = {
    val methods = HttpParser.getClass.getMethods.flatMap { m ⇒
      val n = m.getName
      if (n startsWith "$times") Some(m) else None
    }
    def name(m: Method) = m.getName.substring(6).replace("$minus", "-")
    val names: Seq[String] = methods.map(name)(collection.breakOut)
    val rules: Map[String, Rule1[HttpHeader]] = methods.map { m ⇒
      name(m).toLowerCase -> m.invoke(HttpParser).asInstanceOf[Rule1[HttpHeader]]
    }(collection.breakOut)
    names -> rules
  }

  def parseHeader(header: HttpHeader): Either[ErrorInfo, HttpHeader] = {
    header match {
      case x @ HttpHeaders.RawHeader(name, value) ⇒
        parserRules.get(x.lowercaseName) match {
          case Some(rule) ⇒ parse(rule, value) match {
            case x: Right[_, _] ⇒ x.asInstanceOf[Either[ErrorInfo, HttpHeader]]
            case Left(info)     ⇒ Left(info.withSummaryPrepended("Illegal HTTP header '" + name + '\''))
          }
          case None ⇒ Right(x) // if we don't have a rule for the header we leave it unparsed
        }
      case x ⇒ Right(x) // already parsed
    }
  }

  def parseHeaders(headers: List[HttpHeader]): (List[ErrorInfo], List[HttpHeader]) = {
    @tailrec def parse(headers: List[HttpHeader], errors: List[ErrorInfo] = Nil,
                       parsed: List[HttpHeader] = Nil): (List[ErrorInfo], List[HttpHeader]) =
      if (!headers.isEmpty) parseHeader(headers.head) match {
        case Right(h)    ⇒ parse(headers.tail, errors, h :: parsed)
        case Left(error) ⇒ parse(headers.tail, error :: errors, parsed)
      }
      else errors -> parsed
    parse(headers)
  }

  def parse[A](rule: Rule1[A], input: String): Either[ErrorInfo, A] = {
    try {
      val result = ReportingParseRunner(rule).run(input)
      result.result match {
        case Some(value) ⇒ Right(value)
        case None        ⇒ Left(ErrorInfo(detail = ErrorUtils.printParseErrors(result)))
      }
    } catch {
      case e: ParserRuntimeException ⇒ e.getCause match {
        case e: IllegalUriException ⇒ Left(e.info)
        case _: ParsingException    ⇒ Left(ErrorInfo.fromCompoundString(e.getCause.getMessage))
        case x                      ⇒ throw x
      }
    }
  }
}
