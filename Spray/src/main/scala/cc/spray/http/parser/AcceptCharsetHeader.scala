package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import Charsets._

trait AcceptCharsetHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_CHARSET = rule (
    oneOrMore(CharsetRangeDecl, ListSep) ~ EOI
            ~~> (x => HttpHeaders.`Accept-Charset`(x))
  )
  
  def CharsetRangeDecl = rule (
    CharsetRangeDef ~ optional(CharsetQuality) 
  )
  
  def CharsetRangeDef = rule (
      "*" ~ push(`*`)
    | Charset ~~> (x => Charsets.get(x.toLowerCase).getOrElse(CustomCharset(x)))  
  )
  
  def CharsetQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support charset quality
  }
  
}