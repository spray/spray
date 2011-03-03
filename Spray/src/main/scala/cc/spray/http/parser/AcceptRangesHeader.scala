package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._

trait AcceptRangesHeader {
  this: Parser with ProtocolParameterRules =>

  def ACCEPT_RANGES = rule (
    RangeUnitsDef ~ EOI
            ~~> (x => HttpHeaders.`Accept-Ranges`(x: _*))
  )
  
  def RangeUnitsDef = rule {
    NoRangeUnitsDef | zeroOrMore(RangeUnit, ListSep)
  }
  
  def NoRangeUnitsDef = rule {
    "none" ~ push(List.empty[RangeUnit])
  }
  
}