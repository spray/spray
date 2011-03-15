package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._

trait AcceptHeader {
  this: Parser with ProtocolParameterRules with CommonActions =>

  def ACCEPT = rule (
    zeroOrMore(MediaRangeDecl ~ optional(AcceptParams), ListSep) ~ EOI
      ~~> (HttpHeaders.Accept(_))
  )
  
  def MediaRangeDecl = rule {
    MediaRangeDef ~ zeroOrMore(";" ~ Parameter ~ POP1) // TODO: support parameters    
  }
  
  def MediaRangeDef = rule (
    ("*/*" ~ push("*", "*") | Type ~ "/" ~ ("*" ~ push("*") | Subtype))
      ~~> (getMediaRange(_, _))   
  )
  
  def AcceptParams = rule {
    ";" ~ "q" ~ "=" ~ QValue ~ zeroOrMore(AcceptExtension) // TODO: support qvalues
  }
  
  def AcceptExtension = rule {
    ";" ~ Token ~ optional("=" ~ (Token | QuotedString)) ~ POP2 // TODO: support extensions
  }
  
  // helpers
  
  def getMediaRange(mainType: String, subType: String): MediaRange = {
    if (subType == "*") {
      val mainTypeLower = mainType.toLowerCase
      MediaRanges.get(mainTypeLower).getOrElse(MediaRanges.CustomMediaRange(mainTypeLower))
    } else {
      getMediaType(mainType, subType)
    }
  }
  
}