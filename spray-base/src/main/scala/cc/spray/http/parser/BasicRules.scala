/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.http
package parser

import org.parboiled.scala._
import cc.spray.util.identityFunc

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[spray] object BasicRules extends Parser {

  def Octet = rule { "\u0000" - "\u00FF" }
  
  def Char = rule { "\u0000" - "\u007F" }

  def Alpha = rule { UpAlpha | LoAlpha }

  def UpAlpha = rule { "A" - "Z" }
  
  def LoAlpha = rule { "a" - "z" }
  
  def Digit = rule { "0" - "9" }

  def AlphaNum = Alpha | Digit
  
  def CTL = rule { "\u0000" - "\u001F" | "\u001F" }
  
  def CRLF = rule { str("\r\n") }
  
  def LWS = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }
  
  def Text = rule { !CTL ~ ANY | LWS }
  
  def Hex = rule { "A" - "F" | "a" - "f" | Digit }
  
  def Separator = rule { anyOf("()<>@,;:\\\"/[]?={} \t") } 
  
  def Token: Rule1[String] = rule { oneOrMore(!CTL ~ !Separator ~ ANY) ~> identityFunc }
  
  def Comment: Rule0 = rule { "(" ~ zeroOrMore(CText | QuotedPair ~ DROP | Comment) ~ ")" }
  
  def CText = rule { !anyOf("()") ~ Text }
  
  def QuotedString: Rule1[String] = rule {
    "\"" ~ zeroOrMore(QuotedPair | QDText) ~~> (chars => new String(chars.toArray)) ~ "\""
  }
  
  def QDText: Rule1[Char] = rule { !ch('"') ~ Text ~:> identityFunc }
  
  def QuotedPair: Rule1[Char] = rule { "\\" ~ Char ~:> identityFunc }
  
  // helpers
  
  def OptWS = rule { zeroOrMore(LWS) }
  
  def ListSep = rule { oneOrMore("," ~ OptWS) }

  // Do not accept scoped IPv6 address as it should not be in the Host header:
  //  - WONTFIX in Apache 2 https://issues.apache.org/bugzilla/show_bug.cgi?id=35122
  //  - FIXED in mozilla https://bugzilla.mozilla.org/show_bug.cgi?id=464162
  def IPv6Address = rule { oneOrMore(Hex | anyOf(":.")) }

  def IPv6Reference: Rule1[String] = rule { group("[" ~ IPv6Address ~ "]") ~> identityFunc }
}
