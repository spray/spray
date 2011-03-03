package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import org.parboiled.errors.ErrorUtils

/**
 * Parser for all HTTP headers as defined by
 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 */
object HttpParser extends Parser with ProtocolParameterRules with AdditionalRules with CommonActions
  with AcceptCharsetHeader
  with AcceptEncodingHeader
  with AcceptHeader
  with AcceptLanguageHeader
  with AcceptRangesHeader
  with ContentTypeHeader
  with XForwardedForHeader
  {
  
  def parse[A](rule: Rule1[A], input: String): Either[String, A] = {
    val result = ReportingParseRunner(rule).run(input)
    if (result.matched) {
      Right(result.result)
    } else {
      Left(ErrorUtils.printParseErrors(result))
    }
  }
  
  override implicit def toRule(string :String) : Rule0 = {
    super.toRule(string) ~ OptWS
  }
  
}