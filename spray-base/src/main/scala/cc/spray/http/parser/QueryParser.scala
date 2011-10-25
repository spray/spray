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
import java.net.URLDecoder

object QueryParser extends SprayParser {
  
  def QueryString: Rule1[Map[String, String]] = rule (
      EOI ~ push(Map.empty[String, String])
    | zeroOrMore(QueryParameter, separator = "&") ~ EOI ~~> (_.toMap)
  )
  
  def QueryParameter = rule {
    QueryParameterComponent ~ optional("=") ~ (QueryParameterComponent | push("")) 
  }
  
  def QueryParameterComponent = rule {
    zeroOrMore(!anyOf("&=") ~ ANY) ~> (s => URLDecoder.decode(s, "UTF8"))
  }
  
  def parse(queryString: String): Map[String, String] = {
    try {
      parse(QueryString, queryString) match {
        case Left(error) => throw new RuntimeException(error)
        case Right(parameterMap) => parameterMap
      }
    } catch {
      case e: Exception => throw new HttpException(StatusCodes.BadRequest,
          "Illegal query string '" + queryString + "':\n" + e.getMessage)
    }
  }
  
}