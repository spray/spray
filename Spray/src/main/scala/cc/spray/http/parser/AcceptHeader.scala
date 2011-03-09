package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._

trait AcceptHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def ACCEPT = rule (
    zeroOrMore(MediaRange ~ optional(AcceptParams), ListSep) ~ EOI
      ~~> (HttpHeaders.Accept(_))
  )
  
  def MediaRange = rule {
    MediaRangeDef ~ zeroOrMore(";" ~ Parameter ~ POP1) // TODO: support parameters    
  }
  
  def MediaRangeDef = rule (
    ("*/*" ~ push("*", "*") | Type ~ "/" ~ ("*" ~ push("*") | Subtype))
      ~~> (getMimeType(_, _))   
  )
  
  def AcceptParams = rule {
    ";" ~ "q" ~ "=" ~ QValue ~ zeroOrMore(AcceptExtension) // TODO: support qvalues
  }
  
  def AcceptExtension = rule {
    ";" ~ Token ~ optional("=" ~ (Token | QuotedString)) ~ POP2 // TODO: support extensions
  }
  
}