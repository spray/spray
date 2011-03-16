package cc.spray.http.parser

import org.parboiled.scala._
import org.parboiled.errors.ErrorUtils

private[parser] trait SprayParser extends Parser {
  
  def parse[A](rule: Rule1[A], input: String): Either[String, A] = {
    val result = ReportingParseRunner(rule).run(input)
    result.result match {
      case Some(value) => Right(value)
      case None => Left(ErrorUtils.printParseErrors(result)) 
    }
  }
  
}