package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import cc.spray.http.Charsets.CustomCharset

trait AcceptCharsetHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_CHARSET = rule (
    oneOrMore(CharsetDef, ListSep) ~ EOI
            ~~> (x => HttpHeaders.`Accept-Charset`(x: _*))
  )
  
  def CharsetDef = rule (
    (Charset | "*" ~> identity) ~ optional(CharsetQuality) 
            ~~> (x => Charsets.get(x.toLowerCase).getOrElse(CustomCharset(x))) 
  )
  
  def CharsetQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support charset quality
  }
  
}