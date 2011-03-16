package cc.spray.http
package parser

import org.parboiled.scala._

/**
 * Parser for all HTTP headers as defined by
 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 */
object HttpParser extends SprayParser with ProtocolParameterRules with AdditionalRules with CommonActions
  with AcceptCharsetHeader
  with AcceptEncodingHeader
  with AcceptHeader
  with AcceptLanguageHeader
  with AcceptRangesHeader
  with ContentTypeHeader
  with XForwardedForHeader
  {
  
  // all string literals automatically receive an trailing optional whitespace
  override implicit def toRule(string :String) : Rule0 = {
    super.toRule(string) ~ BasicRules.OptWS
  }
  
}