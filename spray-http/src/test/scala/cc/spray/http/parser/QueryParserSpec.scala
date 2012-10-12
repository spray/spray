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

import org.specs2.mutable._
import QueryParser._


class QueryParserSpec extends Specification {
  
  "The QueryParser" should {
    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") === Right(Map("key" -> "value"))
      parseQueryString("key=value&key2=value2") === Right(Map("key" -> "value", "key2" -> "value2"))
    }
    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") === Right(Map("ke%y" -> "value"))
      parseQueryString("key=value%26&key2=value2") === Right(Map("key" -> "value&", "key2" -> "value2"))
    }
    "return an empty Map for an empty query string" in {
      parseQueryString("") === Right(Map())
    }
    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") === Right(Map("key" -> "", "key2" -> ""))
    }
    "accept empty key value pairs" in {
      parseQueryString("&&b&") === Right(Map("b" -> "", "" -> ""))
    }
    "produce a proper error message on illegal query strings" in {
      parseQueryString("a=b=c") === Left {
        RequestErrorInfo(
          "Illegal query string",
          """|Invalid input '=', expected '&' or EOI (line 1, pos 4):
           |a=b=c
           |   ^
           |""".stripMargin
        )
      }
    }
    "throw a proper HttpException on illegal URL encodings" in {
      parseQueryString("a=b%G") ===
        Left(RequestErrorInfo("Illegal query string", "URLDecoder: Incomplete trailing escape (%) pattern"))
    }
  }
  
} 