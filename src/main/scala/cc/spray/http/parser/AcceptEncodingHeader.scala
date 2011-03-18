package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._
import cc.spray.http.Encodings.CustomEncoding

private[parser] trait AcceptEncodingHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_ENCODING = rule (
    oneOrMore(EncodingDef, ListSep) ~ EOI
            ~~> (x => HttpHeaders.`Accept-Encoding`(x))
  )
  
  def EncodingDef = rule (
    (ContentCoding | "*" ~> identity) ~ optional(EncodingQuality)
            ~~> (x => Encodings.getForKey(x).getOrElse(CustomEncoding(x))) 
  )
  
  def EncodingQuality = rule {
    ";" ~ "q" ~ "=" ~ QValue  // TODO: support encoding quality
  }
  
}