/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.http
package parser

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import org.parboiled.scala._
import spray.util.identityFunc

// direct implementation of http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2
private[parser] object BasicRules extends Parser {

  def Octet = rule { "\u0000" - "\u00FF" }

  def Char = rule { "\u0000" - "\u007F" }

  def Alpha = rule { LoAlpha | UpAlpha }

  def UpAlpha = rule { "A" - "Z" }

  def LoAlpha = rule { "a" - "z" }

  def Digit = rule { "0" - "9" }

  def AlphaNum = rule { Alpha | Digit }

  def CTL = rule { "\u0000" - "\u001F" | "\u007F" }

  def CRLF = rule { str("\r\n") }

  def LWS = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }

  def SP = rule { str(" ") }

  def Text = rule { !CTL ~ ANY | LWS }

  def Hex = rule { "A" - "F" | "a" - "f" | Digit }

  def Separator = rule { anyOf("()<>@,;:\\\"/[]?={} \t") }

  def Token: Rule1[String] = rule { oneOrMore(TokenChar) ~> identityFunc }

  // contrary to the spec we do not allow nested comments
  def Comment = rule {
    "(" ~ push(new JStringBuilder) ~ zeroOrMore(QDText(anyOf("()"))) ~ ")" ~~> (_.toString)
  }

  def QuotedString = rule {
    "\"" ~ push(new JStringBuilder) ~ zeroOrMore(QDText(ch('"'))) ~ "\"" ~~> (_.toString)
  }

  def QDText(excluded: Rule0) =
    ("\\" ~ Char | !excluded ~ Text) ~ toRunAction(c ⇒ c.getValueStack.peek.asInstanceOf[JStringBuilder].append(c.getFirstMatchChar))

  // helpers

  def TokenChar = rule { !CTL ~ !Separator ~ ANY }

  def OptWS = rule { zeroOrMore(LWS) }

  def ListSep = rule { oneOrMore("," ~ OptWS) }

  def IPv4Address: Rule4[Byte, Byte, Byte, Byte] = {
    def IpNumber = {
      def Digit04 = rule { "0" - "4" }
      def Digit05 = rule { "0" - "5" }
      def Digit19 = rule { "1" - "9" }
      rule {
        group(
          ch('2') ~ (Digit04 ~ Digit | ch('5') ~ Digit05)
            | ch('1') ~ Digit ~ Digit
            | Digit19 ~ Digit
            | Digit) ~> (java.lang.Integer.parseInt(_).toByte)
      }
    }
    rule { IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber ~ ch('.') ~ IpNumber ~ OptWS }
  }

  def IPv6Address: Rule1[Array[Byte]] = {
    import org.parboiled.{ Context ⇒ PC }
    def arr(ctx: PC[Any]): Array[Byte] = ctx.getValueStack.peek().asInstanceOf[Array[Byte]]
    def zero(ix: Int, count: Int = 1): PC[Any] ⇒ Unit =
      ctx ⇒ { val a = arr(ctx); java.util.Arrays.fill(a, ix, ix + count, 0.toByte) }
    def h4(ix: Int) = Hex ~ ((ctx: PC[Any]) ⇒ arr(ctx)(ix) = CharUtils.hexValue(ctx.getFirstMatchChar).toByte)
    def h8(ix: Int) = Hex ~ Hex ~ ((ctx: PC[Any]) ⇒
      arr(ctx)(ix) = (CharUtils.hexValue(ctx.getInputBuffer.charAt(ctx.getCurrentIndex - 2)) * 16 +
        CharUtils.hexValue(ctx.getInputBuffer.charAt(ctx.getCurrentIndex - 1))).toByte)
    def h16(ix: Int): Rule0 = h8(ix) ~ h8(ix + 1) | h4(ix) ~ h8(ix + 1) | zero(ix) ~ h8(ix + 1) | zero(ix) ~ h4(ix + 1)
    def h16c(ix: Int): Rule0 = h16(ix) ~ ch(':') ~ !ch(':')
    def ch16o(ix: Int): Rule0 = optional(ch(':') ~ !ch(':')) ~ (h16(ix) | zero(ix, 2))
    def ls32: Rule0 = rule(
      h16(12) ~ ch(':') ~ h16(14)
        | IPv4Address ~~% withContext((a, b, c, d, cx) ⇒ { arr(cx)(12) = a; arr(cx)(13) = b; arr(cx)(14) = c; arr(cx)(15) = d }))
    def cc(ix: Int): Rule0 = ch(':') ~ ch(':') ~ zero(ix, 2)
    def tail2 = rule { h16c(2) ~ tail4 }
    def tail4 = rule { h16c(4) ~ tail6 }
    def tail6 = rule { h16c(6) ~ tail8 }
    def tail8 = rule { h16c(8) ~ tail10 }
    def tail10 = rule { h16c(10) ~ ls32 }
    rule(
      !(ch(':') ~ Hex) ~ push(new Array[Byte](16)) ~ (
        h16c(0) ~ tail2
        | cc(0) ~ tail2
        | ch16o(0) ~ (
          cc(2) ~ tail4
          | ch16o(2) ~ (
            cc(4) ~ tail6
            | ch16o(4) ~ (
              cc(6) ~ tail8
              | ch16o(6) ~ (
                cc(8) ~ tail10
                | ch16o(8) ~ (
                  cc(10) ~ ls32
                  | ch16o(10) ~ (
                    cc(12) ~ h16(14)
                    | ch16o(12) ~ cc(14)))))))))
  }

  def IPv6Reference: Rule1[String] = rule { group("[" ~ oneOrMore(Hex | anyOf(":.")) ~ "]") ~> identityFunc }
}
