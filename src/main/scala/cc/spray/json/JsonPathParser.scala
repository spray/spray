package cc.spray.json

import org.parboiled.scala._
import org.parboiled.errors.{ErrorUtils, ParsingException}
import com.sun.xml.internal.bind.WhiteSpaceProcessor

object JsonPathParser extends Parser {
  def JsonPathExpr = rule { Path ~ EOI }

  def Path: Rule1[JsonPath.Path] = rule { Root ~ OptionalSelection }

  def Root: Rule1[JsonPath.Root.type] = rule {
    // we don't distinguish between '$' and '@'
    anyOf("$@") ~ push(JsonPath.Root)
  }

  /*
   * To remove the left-recursion I had to factor out Root to here
   */
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
    Lt | Eq | Exists
  }
  def Eq: Rule1[JsonPath.Eq] = rule { op("==")(JsonPath.Eq) }
  def Lt: Rule1[JsonPath.Lt] = rule { op("<")(JsonPath.Lt) }
  def Exists: Rule1[JsonPath.Exists] = rule {
    Path ~~> JsonPath.Exists
  }

  def op[T](op: String)(cons: (JsonPath.Expr, JsonPath.Expr) => T) =
    Expr ~ WhiteSpace ~ op ~ WhiteSpace ~ Expr ~~> cons

  def Expr: Rule1[JsonPath.Expr] = rule {
    Path ~~> JsonPath.PathExpr |
    Constant
  }
  def Constant: Rule1[JsonPath.Constant] = rule {
    JsonParser.Integer ~> (d => JsonPath.Constant(d.toInt)) |
    SingleQuotedString ~~> JsonPath.Constant
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

object JsonPath {
  sealed trait Path
  case object Root extends Path
  case class Selection(previous: Path, projection: Projection) extends Path

  sealed trait Projection
  case object AllElements extends Projection
  case class ByField(name: String) extends Projection
  case class ByIndex(idx: Int) extends Projection
  case class ByPredicate(expr: Predicate) extends Projection

  sealed trait Predicate
  case class Eq(expr1: Expr, expr2: Expr) extends Predicate
  case class Lt(expr1: Expr, expr2: Expr) extends Predicate
  case class Exists(path: Path) extends Predicate

  sealed trait Expr
  case class PathExpr(path: Path) extends Expr
  case class Constant(value: Any) extends Expr
}