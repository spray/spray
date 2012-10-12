/*
 * Copyright (C) 2011-2012 spray.io
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

import org.parboiled.scala._
import java.net.URLDecoder
import java.io.UnsupportedEncodingException
import org.parboiled.errors.ParsingException


object QueryParser extends SprayParser {
  
  val QueryString: Rule1[QueryParams] = rule (
      EOI ~ push(Map.empty[String, String])
    | zeroOrMore(QueryParameter, separator = "&") ~ EOI ~~> (_.toMap)
  )
  
  def QueryParameter = rule {
    QueryParameterComponent ~ optional("=") ~ (QueryParameterComponent | push("")) 
  }
  
  def QueryParameterComponent = rule {
    zeroOrMore(!anyOf("&=") ~ ANY) ~> { s =>
      try URLDecoder.decode(s, "UTF8")
      catch {
        case e: IllegalArgumentException =>
          throw new ParsingException("Illegal query string: " + e.getMessage)
        case e: UnsupportedEncodingException =>
          throw new ParsingException("Unsupported character encoding in query string: " + e.getMessage)
      }
    }
  }
  
  def parseQueryString(queryString: String): Either[RequestErrorInfo, QueryParams] =
    parse(QueryString, queryString).left.map(_.withFallbackSummary("Illegal query string"))
  
}