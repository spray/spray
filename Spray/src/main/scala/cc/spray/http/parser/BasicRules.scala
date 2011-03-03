package cc.spray.http
package parser

import org.parboiled.scala._

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
object BasicRules extends Parser {

  def Octet = rule { "\u0000" - "\u00FF" }
  
  def Char = rule { "\u0000" - "\u007F" }

  def Alpha = rule { UpAlpha | LoAlpha }

  def UpAlpha = rule { "A" - "Z" }
  
  def LoAlpha = rule { "a" - "z" }
  
  def Digit = rule { "0" - "9" }
  
  def CTL = rule { "\u0000" - "\u001F" | "\u001F" }
  
  def CRLF = rule { str("\r\n") }
  
  def LWS = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }
  
  def Text = rule { !CTL ~ ANY | LWS }
  
  def Hex = rule { "A" - "F" | "a" - "f" | Digit }
  
  def Separator = rule { anyOf("()<>@,;:\\\"/[]?={} \t") } 
  
  def Token: Rule1[String] = rule { oneOrMore(!CTL ~ !Separator ~ ANY) ~> identity }
  
  def Comment: Rule0 = rule { "(" ~ zeroOrMore(CText | QuotedPair | Comment) ~ ")" }
  
  def CText = rule { !anyOf("()") ~ Text }
  
  def QuotedString: Rule1[String] = rule { "\"" ~ zeroOrMore(QDText | QuotedPair) ~> identity ~ "\"" }
  
  def QDText = rule { !ch('"') ~ Text }
  
  def QuotedPair = rule { "\\" ~ Char }
  
  // helpers
  
  def OptWS = rule { zeroOrMore(LWS) }
  
  def ListSep = rule { oneOrMore("," ~ OptWS) }
}