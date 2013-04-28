/*
 * Copyright (C) 2009-2011 Mathias Doenitz
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

package spray.json

import org.parboiled.scala._
import org.parboiled.errors.{ ErrorUtils, ParsingException }
import org.parboiled.Context
import java.lang.StringBuilder

/**
 * This JSON parser is the almost direct implementation of the JSON grammar
 * presented at http://www.json.org as a parboiled PEG parser.
 */
trait JsonParser extends Parser {

  // the root rule
  lazy val Json = rule { WhiteSpace ~ Value ~ EOI }

  def JsonObject: Rule1[JsObject] = rule {
    "{ " ~ zeroOrMore(Pair, separator = ", ") ~ "} " ~~> (JsObject(_: _*))
  }

  def Pair = rule { JsonStringUnwrapped ~ ": " ~ Value ~~> ((_, _)) }

  def Value: Rule1[JsValue] = rule {
    JsonString | JsonNumber | JsonObject | JsonArray | JsonTrue | JsonFalse | JsonNull
  }

  def JsonString = rule { JsonStringUnwrapped ~~> (JsString(_)) }

  def JsonStringUnwrapped = rule { "\"" ~ Characters ~ "\" " ~~> (_.toString) }

  def JsonNumber = rule { group(Integer ~ optional(Frac) ~ optional(Exp)) ~> (JsNumber(_)) ~ WhiteSpace }

  def JsonArray = rule { "[ " ~ zeroOrMore(Value, separator = ", ") ~ "] " ~~> (JsArray(_)) }

  def Characters = rule { push(new StringBuilder) ~ zeroOrMore("\\" ~ EscapedChar | NormalChar) }

  def EscapedChar = rule(
    anyOf("\"\\/") ~:% withContext(appendToSb(_)(_))
      | "b" ~ appendToSb('\b')
      | "f" ~ appendToSb('\f')
      | "n" ~ appendToSb('\n')
      | "r" ~ appendToSb('\r')
      | "t" ~ appendToSb('\t')
      | Unicode ~~% withContext((code, ctx) ⇒ appendToSb(code.asInstanceOf[Char])(ctx)))

  def NormalChar = rule { !anyOf("\"\\") ~ ANY ~:% (withContext(appendToSb(_)(_))) }

  def Unicode = rule { "u" ~ group(HexDigit ~ HexDigit ~ HexDigit ~ HexDigit) ~> (java.lang.Integer.parseInt(_, 16)) }

  def Integer = rule { optional("-") ~ (("1" - "9") ~ Digits | Digit) }

  def Digits = rule { oneOrMore(Digit) }

  def Digit = rule { "0" - "9" }

  def HexDigit = rule { "0" - "9" | "a" - "f" | "A" - "Z" }

  def Frac = rule { "." ~ Digits }

  def Exp = rule { ignoreCase("e") ~ optional(anyOf("+-")) ~ Digits }

  def JsonTrue = rule { "true " ~ push(JsTrue) }

  def JsonFalse = rule { "false " ~ push(JsFalse) }

  def JsonNull = rule { "null " ~ push(JsNull) }

  def WhiteSpace: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  // helper method for fast string building
  // for maximum performance we use a somewhat unorthodox parsing technique that is a bit more verbose (and somewhat
  // less readable) but reduces object allocations during the parsing run to a minimum:
  // the Characters rules pushes a StringBuilder object onto the stack which is then directly fed with matched
  // and unescaped characters in the sub rules (i.e. no string allocations and value stack operation required)
  def appendToSb(c: Char): Context[Any] ⇒ Unit = { ctx ⇒
    ctx.getValueStack.peek.asInstanceOf[StringBuilder].append(c)
    ()
  }

  /**
   * We redefine the default string-to-rule conversion to also match trailing whitespace if the string ends with
   * a blank, this keeps the rules free from most whitespace matching clutter
   */
  override implicit def toRule(string: String) = {
    if (string.endsWith(" ")) str(string.trim) ~ WhiteSpace
    else str(string)
  }

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(json: String): JsValue = apply(json.toCharArray)

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(json: Array[Char]): JsValue = {
    val parsingResult = ReportingParseRunner(Json).run(json)
    parsingResult.result.getOrElse {
      throw new ParsingException("Invalid JSON source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }

}
object JsonParser extends JsonParser
