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

class ReadmeSpec extends Specification {

  "The Usage snippets" should {
    "behave as expected" in {
      val source = """{ "some": "JSON source" }"""
      val jsonAst = source.asJson
      jsonAst mustEqual JsObject("some" -> JsString("JSON source"))

      val json2 = jsonAst.formatPretty
      json2 mustEqual
              """{
                |  "some": "JSON source"
                |}""".stripMargin

      val jsonAst2 = List(1, 2, 3).toJson
      jsonAst2 mustEqual JsArray(JsNumber(1), JsNumber(2), JsNumber(3))
    }
  }

  case class Color(name: String, red: Int, green: Int, blue: Int)
  val color = Color("CadetBlue", 95, 158, 160)

  "The case class example" should {
    "behave as expected" in {
      object MyJsonProtocol extends ProductFormats {
        implicit val colorFormat = jsonFormat4(Color)
      }
      import MyJsonProtocol._
      color.toJson.as[Color] mustEqual color
    }
  }

  "The non case class (array) example" should {
    "behave as expected" in {
      object MyJsonProtocol {
        implicit object ColorJsonFormat extends JsonFormat[Color] {
          def write(c: Color) =
            JsArray(JsString(c.name), JsNumber(c.red), JsNumber(c.green), JsNumber(c.blue))

          def read(value: JsValue) = value match {
            case JsArray(Seq(JsString(name), JsNumber(red), JsNumber(green), JsNumber(blue))) =>
              Validated(new Color(name, red.toInt, green.toInt, blue.toInt))
            case _ => deserializationError("Color expected")
          }
        }
      }
      import MyJsonProtocol._

      color.toJson.as[Color] mustEqual color
    }
  }

  "The non case class (object) example" should {
      "behave as expected" in {
        object MyJsonProtocol {
          implicit object ColorJsonFormat extends JsonFormat[Color] {
            def write(c: Color) = JsObject(
              "name" -> JsString(c.name),
              "red" -> JsNumber(c.red),
              "green" -> JsNumber(c.green),
              "blue" -> JsNumber(c.blue)
            )
            def read(json: JsValue) = {
              for {
                name: String <- json("name")
                red: Int <- json("red")
                green: Int <- json("green")
                blue: Int <- json("blue")
              } yield Color(name, red, green, blue)
            }
          }
        }
        import MyJsonProtocol._
        color.toJson.as[Color] mustEqual color
      }
    }

}