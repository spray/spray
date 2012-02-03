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
import org.parboiled.common.FileUtils

class JsonParserSpec extends Specification {

  "The JsonParser" should {
    "parse 'null' to JsNull" in {
      JsonParser("null") mustEqual JsNull
    }
    "parse 'true' to JsTrue" in {
      JsonParser("true") mustEqual JsTrue
    }
    "parse 'false' to JsFalse" in {
      JsonParser("false") mustEqual JsFalse
    }
    "parse '0' to JsNumber" in {
      JsonParser("0") mustEqual JsNumber(0)
    }
    "parse '1.23' to JsNumber" in {
      JsonParser("1.23") mustEqual JsNumber(1.23)
    }
    "parse '-1E10' to JsNumber" in {
      JsonParser("-1E10") mustEqual JsNumber("-1E+10")
    }
    "parse '12.34e-10' to JsNumber" in {
      JsonParser("12.34e-10") mustEqual JsNumber("1.234E-9")
    }
    "parse \"xyz\" to JsString" in {
      JsonParser("\"xyz\"") mustEqual JsString("xyz")
    }
    "parse escapes in a  JsString" in {
      JsonParser(""""\"\\/\b\f\n\r\t\u12Ab"""") mustEqual JsString("\"\\/\b\f\n\r\t\u12ab")
    }
    "properly parse a simple JsObject" in (
      JsonParser(""" { "key" :42, "key2": "value" }""") mustEqual
              JsObject("key" -> JsNumber(42), "key2" -> JsString("value"))
    )
    "properly parse a simple JsArray" in (
      JsonParser("""[null, 1.23 ,{"key":true } ] """) mustEqual
              JsArray(JsNull, JsNumber(1.23), JsObject("key" -> JsBoolean(true)))
    )
    "be reentrant" in {
      val largeJsonSource = FileUtils.readAllCharsFromResource("test.json")
      List.fill(20)(largeJsonSource).par.map(JsonParser(_)).toList.map {
          _.asInstanceOf[JsObject].fields("questions").asInstanceOf[JsArray].elements.size
      } mustEqual List.fill(20)(100)
    }
  }

}