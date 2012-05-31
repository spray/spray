package cc.spray.json

import org.parboiled.scala._
import org.parboiled.errors.{ErrorUtils, ParsingException}

object JsonPathParser extends Parser {
  def JsonPathExpr = rule { Tree ~ EOI }

  def Tree: Rule1[JsonPath.Expr] = rule { Root ~ OptionalSelection }

  def Root: Rule1[JsonPath.Root.type] = rule { "$" ~ push(JsonPath.Root) }

  /*
   * To remove the left-recursion I had to factor out Root to here
   */
  def OptionalSelection : ReductionRule1[JsonPath.Expr, JsonPath.Expr] = rule {
    Projection ~~> JsonPath.Selection ~ OptionalSelection |
    EMPTY ~~> identity
  }

  def Projection: Rule1[JsonPath.Projection] = rule {
    "." ~ DotProjection          |
    "[" ~ BracketProjection ~"]"
  }

  def DotProjection: Rule1[JsonPath.Projection] = rule {
    AllElements | ByFieldName
  }
  def AllElements = rule { "*" ~ push(JsonPath.AllElements) }
  def ByFieldName = rule { FieldName ~~> JsonPath.ByField }

  def BracketProjection: Rule1[JsonPath.Projection] = rule {
    JsonParser.Digits ~> (d => JsonPath.ByIndex(d.toInt)) |
    SingleQuotedString ~~> JsonPath.ByField
  }

  def FieldName: Rule1[String] = rule {
    oneOrMore(!anyOf(".") ~ ANY) ~> identity
  }

  def SingleQuotedString: Rule1[String] =
    rule { "'" ~ push(new java.lang.StringBuilder) ~ zeroOrMore(!anyOf("'") ~ ("\\" ~ JsonParser.EscapedChar | JsonParser.NormalChar)) } ~ "'" ~~> (_.toString)

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(path: String): JsonPath.Expr = apply(path.toCharArray)

  /**
   * The main parsing method. Uses a ReportingParseRunner (which only reports the first error) for simplicity.
   */
  def apply(path: Array[Char]): JsonPath.Expr = {
    val parsingResult = ReportingParseRunner(JsonPathExpr).run(path)
    parsingResult.result.getOrElse {
      throw new ParsingException("Invalid JSON source:\n" + ErrorUtils.printParseErrors(parsingResult))
    }
  }
}

object JsonPath {
  sealed trait Expr
  case object Root extends Expr
  case class Selection(previous: Expr, projection: Projection) extends Expr

  sealed trait Projection
  case object AllElements extends Projection
  case class ByField(name: String) extends Projection
  case class ByIndex(idx: Int) extends Projection
  //case class ByPredicate()
}