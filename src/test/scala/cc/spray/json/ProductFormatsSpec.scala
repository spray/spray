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

class ProductFormatsSpec extends Specification {

  case class Test2(a: Int, b: Option[Double])
  case class Test3[A, B](as: List[A], bs: List[B])

  trait TestProtocol {
    this: DefaultJsonProtocol =>
    implicit val test2Format = jsonFormat2(Test2)
    implicit def test3Format[A: JsonFormat, B: JsonFormat] = jsonFormat2(Test3.apply[A, B])
  }
  object TestProtocol1 extends DefaultJsonProtocol with TestProtocol
  object TestProtocol2 extends DefaultJsonProtocol with TestProtocol with NullOptions

  "A JsonFormat created with `jsonFormat`, for a case class with 2 elements," should {
    import TestProtocol1._
    val obj = Test2(42, Some(4.2))
    val json = JsObject("a" -> JsNumber(42), "b" -> JsNumber(4.2))
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.convertTo[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsObject does not all required members" in (
      JsObject("b" -> JsNumber(4.2)).convertTo[Test2] must
              throwA(new DeserializationException("Object is missing required member 'a'"))
    )
    "not require the presence of optional fields for deserialization" in {
      JsObject("a" -> JsNumber(42)).convertTo[Test2] mustEqual Test2(42, None)
    }
    "not render `None` members during serialization" in {
      Test2(42, None).toJson mustEqual JsObject("a" -> JsNumber(42))
    }
    "ignore additional members during deserialization" in {
      JsObject("a" -> JsNumber(42), "b" -> JsNumber(4.2), "c" -> JsString('no)).convertTo[Test2] mustEqual obj
    }
    "not depend on any specific member order for deserialization" in {
      JsObject("b" -> JsNumber(4.2), "a" -> JsNumber(42)).convertTo[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsValue is not a JsObject" in (
      JsNull.convertTo[Test2] must throwA(new DeserializationException("Object expected"))
    )
  }

  "A JsonProtocol mixing in NullOptions" should {
    "render `None` members to `null`" in {
      import TestProtocol2._
      Test2(42, None).toJson mustEqual JsObject("a" -> JsNumber(42), "b" -> JsNull)
    }
  }

  "A JsonFormat for a generic case class and created with `jsonFormat`" should {
    import TestProtocol1._
    val obj = Test3(42 :: 43 :: Nil, "x" :: "y" :: "z" :: Nil)
    val json = JsObject(
      "as" -> JsArray(JsNumber(42), JsNumber(43)),
      "bs" -> JsArray(JsString("x"), JsString("y"), JsString("z"))
    )
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.convertTo[Test3[Int, String]] mustEqual obj
    }
  }

}
