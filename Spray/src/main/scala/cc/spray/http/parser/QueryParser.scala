package cc.spray.http
package parser

import org.parboiled.scala._

object QueryParser extends SprayParser {
  
  def QueryString: Rule1[Map[String, String]] = rule {
    zeroOrMore(QueryParameter, "&") ~ EOI ~~> (_.toMap) 
  }
  
  def QueryParameter = rule {
    QueryParameterComponent ~ optional("=") ~ (QueryParameterComponent | push("")) 
  }
  
  def QueryParameterComponent = rule {
    oneOrMore(!anyOf("&=") ~ ANY) ~> identity
  }
  
  def parse(queryString: String): Map[String, String] = parse(QueryString, queryString) match {
    case Left(error) => throw new HttpException(HttpStatusCodes.BadRequest, 
      "Illegal query string '" + queryString + "':\n" + error)
    case Right(parameterMap) => parameterMap
  } 
  
}