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

package spray.json

import org.specs2.mutable.Specification

class CustomFormatSpec extends Specification {
  case class MyType(name: String, value: Int)

  implicit val MyTypeProtocol = new RootJsonFormat[MyType] {
    def read(json: JsValue) = {
      for {
        name: String ← json("name").toValidated[String]
        value: Int ← json("value").toValidated[Int]
      } yield MyType(name, value)
    }
    def write(obj: MyType) = JsObject("name" -> JsString(obj.name), "value" -> JsNumber(obj.value))
  }

  "A custom JsonFormat built with 'asJsonObject'" should {
    val value = MyType("bob", 42)
    "correctly deserialize valid JSON content" in {
      """{ "name": "bob", "value": 42 }""".asJson.as[MyType] mustEqual value
    }
    "support full round-trip (de)serialization" in {
      value.toJson.as[MyType] mustEqual value
    }
  }

}