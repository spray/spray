package cc.spray.utils

import java.net.URLDecoder._
import java.net.URLEncoder._

/**
 * This object only deals with the query portion of the URI. A URI can be decomposed into
 * constituant components using the java.lang.URI class.
 * Note: The individual parameters/values are assumed to be encoded, if neccesary
 */
object QueryParser {
  val encoding = "UTF-8"

  def parseQuery(query: String): Map[Symbol, String] =
    Map((query.split("&").toList.flatMap { nameEqualsValue =>
      nameEqualsValue.split("=").toList match {
        case "" :: Nil => Nil
        case name :: Nil => (name, "") :: Nil
        case name :: value :: Nil => (name, value) :: Nil
        case name :: everythingElse => (name, everythingElse.mkString("=")) :: Nil
        case Nil => Nil
      }
    }).map { nameValue =>
      (Symbol(decode(nameValue._1, encoding)), decode(nameValue._2, encoding))
    }: _*)

  def unparseQuery(query: Map[Symbol, String], shouldEncode: Boolean = true): String = {
    def transformFn(nameValue: (Symbol, String)) = {
      if(shouldEncode) 
        (encode(nameValue._1.name, encoding) :: encode(nameValue._2, encoding) :: Nil)
      else
        (nameValue._1.name :: nameValue._2 :: Nil)
    }

    query.map { nameValue =>
      transformFn(nameValue).filter { n => !n.isEmpty }.mkString("=")
    }.mkString("&")
  }
}
