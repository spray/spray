package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import LanguageRanges._

trait AcceptLanguageHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_LANGUAGE = rule (
    oneOrMore(LanguageRangeDef, ListSep) ~ EOI
            ~~> (x => HttpHeaders.`Accept-Language`(x))
  )
  
  def LanguageRangeDef = rule {
    (LanguageTag ~~> (Language(_, _: _*)) | "*" ~ push(`*`)) ~ optional(LanguageQuality) 
  }
  
  def LanguageQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support language quality
  }
  
}