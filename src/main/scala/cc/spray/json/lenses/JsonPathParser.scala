package cc.spray.json
package lenses

import org.parboiled.scala._
import org.parboiled.errors.{ErrorUtils, ParsingException}

/**
 * A parser for json-path expression as specified here:
 * [[http://goessner.net/articles/JsonPath/]]
 */
object JsonPathParser extends Parser {
  def JsonPathExpr = rule { Path ~ EOI }

  def Path: Rule1[JsonPath.Path] = rule { Root ~ OptionalSelection }

  def Root: Rule1[JsonPath.Root.type] = rule {
    // we don't distinguish between '$' and '@'
    anyOf("$@") ~ push(JsonPath.Root)
  }

  def OptionalSelection : ReductionRule1[JsonPath.Path, JsonPath.Path] = rule {
    Projection ~~> JsonPath.Selection ~ OptionalSelection |
    EMPTY ~~> identity
  }

  def Projection: Rule1[JsonPath.Projection] = rule {
    "." ~ DotProjection          |
    "[" ~ BracketProjection ~"]"
  }

  def DotProjection: Rule1[JsonPath.Projection] = rule {
    ByFieldName
  }
  def AllElements = rule { "*" ~ push(JsonPath.AllElements) }
  def ByFieldName = rule { FieldName ~~> JsonPath.ByField }

  import JsonParser.WhiteSpace
  def BracketProjection: Rule1[JsonPath.Projection] = rule {
    JsonParser.Digits ~> (d => JsonPath.ByIndex(d.toInt)) |
    SingleQuotedString ~~> JsonPath.ByField |
    AllElements |
    "?(" ~ WhiteSpace ~ Predicate ~ WhiteSpace ~ ")" ~~> JsonPath.ByPredicate
  }

  def Predicate: Rule1[JsonPath.Predicate] = rule {
    Lt | Gt | Eq | Exists
  }
  def Eq: Rule1[JsonPath.Eq] = rule { op("==")(JsonPath.Eq) }
  def Lt: Rule1[JsonPath.Lt] = rule { op("<")(JsonPath.Lt) }
  def Gt: Rule1[JsonPath.Gt] = rule { op(">")(JsonPath.Gt) }
  def Exists: Rule1[JsonPath.Exists] = rule {
    Path ~~> JsonPath.Exists
  }

  def op[T](op: String)(cons: (JsonPath.Expr, JsonPath.SimpleExpr) => T) =
    Expr ~ WhiteSpace ~ op ~ WhiteSpace ~ SimpleExpr ~~> cons

  def Expr: Rule1[JsonPath.Expr] = rule {
    Path ~~> JsonPath.PathExpr |
    SimpleExpr
  }
  def SimpleExpr: Rule1[JsonPath.SimpleExpr] = rule {
    JsConstant ~~> JsonPath.Constant
  }
  def JsConstant: Rule1[JsValue] = rule {
    JsonParser.JsonNumber |
    SingleQuotedString ~~> (JsString(_))
  }

  val WhiteSpaceChars = " \n\r\t\f"
  def FieldName: Rule1[String] = rule {
    oneOrMore(!anyOf(".[)]"+WhiteSpaceChars) ~ ANY) ~> identity
  }

  def SingleQuotedString: Rule1[String] =
    rule { "'" ~ push(new java.lang.StringBuilder) ~ zeroOrMore(!anyOf("'") ~ ("\\" ~ JsonParser.EscapedChar | JsonParser.NormalChar)) } ~ "'" ~~> (_.toString)

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(path: String): JsonPath.Path = apply(path.toCharArray)

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(path: Array[Char]): JsonPath.Path = {
    val parsingResult = ReportingParseRunner(JsonPathExpr).run(path)
    parsingResult.result.getOrElse {
      throw new ParsingException("Invalid JSON source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }
}
