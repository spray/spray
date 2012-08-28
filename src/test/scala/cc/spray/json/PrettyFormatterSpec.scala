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

package cc.spray.json

import org.specs2.mutable._

class PrettyFormatterSpec extends Specification {

  "The PrettyFormatter" should {
    "print a more complicated JsObject nicely aligned" in {
      PrettyFormatter {
        JsonParser {
          """|{
             |  "simpleKey" : "some value",
             |  "key with spaces": null,
             |  "zero": 0,
             |  "number": -1.2323424E-5,
             |  "Boolean yes":true,
             |  "Boolean no": false,
             |  "Unic\u00f8de" :  "Long string with newline\nescape",
             |  "key with \"quotes\"" : "string",
             |  "sub object" : {
             |    "sub key": 26.5,
             |    "a": "b",
             |    "array": [1, 2, { "yes":1, "no":0 }, ["a", "b", null], false]
             |  }
             |}""".stripMargin
        }
      } mustEqual {
        """|{
           |  "simpleKey": "some value",
           |  "key with spaces": null,
           |  "zero": 0,
           |  "number": -0.000012323424,
           |  "Boolean yes": true,
           |  "Boolean no": false,
           |  "Unic\u00f8de": "Long string with newline\nescape",
           |  "key with \"quotes\"": "string",
           |  "sub object": {
           |    "sub key": 26.5,
           |    "a": "b",
           |    "array": [1, 2, {
           |      "yes": 1,
           |      "no": 0
           |    }, ["a", "b", null], false]
           |  }
           |}""".stripMargin.replace("\u00f8", "\\u00f8")
      }
    }
  }
  
}