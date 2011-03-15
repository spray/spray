package cc.spray.http
package parser

import org.parboiled.scala._
import BasicRules._

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html
trait ProtocolParameterRules {
  this: Parser =>

  /* 3.1 HTTP Version */
  
  def HttpVersion = rule { "HTTP" ~ "/" ~ oneOrMore(Digit) ~ "." ~ oneOrMore(Digit) }

  
  /* 3.3 Date/Time Formats */
  
  /* 3.3.1 Full Date */
  
  def HttpDate = rule { (RFC1123Date | RFC850Date | ASCTimeDate) ~ OptWS }
  
  def RFC1123Date = rule { Wkday ~ str(", ") ~ Date1 ~ ch(' ') ~ Time ~ ch(' ') ~ str("GMT") } 
  
  def RFC850Date = rule { Weekday ~ str(", ") ~ Date2 ~ ch(' ') ~ Time ~ ch(' ') ~ str("GMT") }
  
  def ASCTimeDate = rule { Wkday ~ ch(' ') ~ Date3 ~ ch(' ') ~ Time ~ ch(' ') ~ Digit ~ Digit ~ Digit ~ Digit }
  
  def Date1 = rule { Digit ~ Digit ~ ch(' ') ~ Month ~ ch(' ') ~ Digit ~ Digit ~ Digit ~ Digit }
  
  def Date2 = rule { Digit ~ Digit ~ ch('-') ~ Month ~ ch('-') ~ Digit ~ Digit ~ Digit ~ Digit }
  
  def Date3 = rule { Month ~ ch(' ') ~ (Digit ~ Digit | ch(' ') ~ Digit) }
  
  def Time = rule { Digit ~ Digit ~ ch(':') ~ Digit ~ Digit ~ ch(':') ~ Digit ~ Digit }
  
  def Wkday = rule { "Mon" ~ "Tue" ~ "Wed" ~ "Thu" ~ "Fri" ~ "Sat" ~ "Sun" }
  
  def Weekday = rule { "Monday" ~ "Tuesday" ~ "Wednesday" ~ "Thursday" ~ "Friday" ~ "Saturday" ~ "Sunday" }
  
  def Month = rule { "Jan" ~ "Feb" ~ "Mar" ~ "Apr" ~ "May" ~ "Jun" ~ "Jul" ~ "Aug" ~ "Sep" ~ "Oct" ~ "Nov" ~ "Dec" }
  
  /* 3.3.2 Delta Seconds */
  
  def DeltaSeconds = rule { oneOrMore(Digit) }
  
  
  /* 3.4 Character Sets */
  
  def Charset = rule { Token }
  
  
  /* 3.5 Content Codings */
  
  def ContentCoding = rule { Token }
  
  
  /* 3.6 Transfer Codings */
  
  def TransferCoding = rule { "chunked" | TransferExtension ~ POP2 }
  
  def TransferExtension = rule { Token ~ zeroOrMore(";" ~ Parameter) }
  
  def Parameter = rule { Attribute ~ "=" ~ Value ~~> ((_, _)) }
  
  def Attribute = rule { Token }
  
  def Value = rule { Token | QuotedString }
  
  /* 3.6.1 Chunked Transfer Codings */
  
  // TODO: implement chunked transfers
  
  
  /* 3.7 Media Types */
  
  def MediaTypeDef: Rule3[String, String, Map[String, String]] = rule {
    Type ~ "/" ~ Subtype ~ zeroOrMore(";" ~ Parameter) ~~> (_.toMap)
  } 
  
  def Type = rule { Token }
  
  def Subtype = rule { Token }
  
  
  /* 3.8 Product Tokens */
  
  def Product = rule { Token ~ optional("/" ~ ProductVersion) }
  
  def ProductVersion = rule { Token }
  
  
  /* 3.9 Quality Values */
  
  def QValue = rule (
      // more loose than the spec which only allows 1 to max. 3 digits/zeros
      ch('0') ~ optional(ch('.') ~ zeroOrMore(Digit)) ~ OptWS
    | ch('1') ~ optional(ch('.') ~ zeroOrMore(ch('0'))) ~ OptWS
  )
  
  
  /* 3.10 Language Tags */
  
  def LanguageTag = rule { PrimaryTag ~ zeroOrMore("-" ~ SubTag) }
  
  // more loose than the spec which only allows 1 to max. 8 alphas
  def PrimaryTag = rule { oneOrMore(Alpha) ~> identity ~ OptWS } 
  
  // more loose than the spec which only allows 1 to max. 8 alphas
  def SubTag = rule { oneOrMore(Alpha) ~> identity ~ OptWS }
  
  
  /* 3.11 Entity Tags */
  
  def EntityTag = rule { optional("W/") ~ OpaqueTag }
  
  def OpaqueTag = rule { QuotedString }
  
  
  /* 3.12 Range Units */
  
  def RangeUnit = rule { BytesUnit | OtherRangeUnit }
  
  def BytesUnit = rule { "bytes" ~ push(RangeUnits.bytes) }
  
  def OtherRangeUnit = rule { Token ~~> RangeUnits.CustomRangeUnit }
}