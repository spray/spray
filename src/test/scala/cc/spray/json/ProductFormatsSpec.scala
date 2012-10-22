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

  case class Test(a: Int)

  case class Test2(a: Int, b: Option[Double])
  object Test2 extends ProductFormats {
    implicit val test2Format = jsonFormat(Test2.apply _)("a", "b")
  }
  case class Test3[A, B](as: List[A], bs: List[B])
  object Test3 extends ProductFormats {
    implicit def test3Format[A: JsonFormat, B: JsonFormat] = jsonFormat2(Test3.apply[A, B])
  }

  "A JsonFormat created with `jsonFormat`, for a case class with 2 elements," should {
    val obj = Test2(42, Some(4.2))
    val json = JsObject("a" -> JsNumber(42), "b" -> JsNumber(4.2))
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.as[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsObject does not all required members" in (
      JsObject("b" -> JsNumber(4.2)).as[Test2] must
              throwA(new DeserializationException("JsObject is missing required member 'a'"))
    )
    "not require the presence of optional fields for deserialization" in {
      JsObject("a" -> JsNumber(42)).as[Test2] mustEqual Test2(42, None)
    }
    "not render `None` members during serialization" in {
      Test2(42, None).toJson mustEqual JsObject("a" -> JsNumber(42))
    }
    "ignore additional members during deserialization" in {
      JsObject("a" -> JsNumber(42), "b" -> JsNumber(4.2), "c" -> JsString('no)).as[Test2] mustEqual obj
    }
    "not depend on any specific member order for deserialization" in {
      JsObject("b" -> JsNumber(4.2), "a" -> JsNumber(42)).as[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsValue is not a JsObject" in (
      JsNull.as[Test2] must throwA(new DeserializationException("Expected JsObject but got JsNull$"))
    )
  }

  "A JsonProtocol mixing in NullOptions" should {
    "render `None` members to `null`" in {
      import ProductFormats._
      implicit val test2Format = jsonFormat(Test2.apply _)("a", "b".optionAsNull)
      Test2(42, None).toJson mustEqual JsObject("a" -> JsNumber(42), "b" -> JsNull)
    }
  }

  "A JsonFormat for a generic case class and created with `jsonFormat`" should {
    val obj = Test3(42 :: 43 :: Nil, "x" :: "y" :: "z" :: Nil)
    val json = JsObject(
      "as" -> JsArray(JsNumber(42), JsNumber(43)),
      "bs" -> JsArray(JsString("x"), JsString("y"), JsString("z"))
    )
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.as[Test3[Int, String]] mustEqual obj
    }
  }

  "A JsonFormat for a product" should {
    "be customizable" in {
      import ProductFormats._

      "by mapping formats" in {
        implicit val format = jsonFormat(Test)("a".as[String].using(_.toInt, _.toString))

        Test(5).toJson must be_==(JsObject("a" -> JsString("5")))
      }
      "by ignoring some product fields" in {
        implicit val format = jsonFormat(Test)(ignore(35))

        JsObject().as[Test] must be_==(Test(35))
        Test(1276).toJson must be_==(JsObject())
      }
      "by interpreting a value as the missing field" in {
        implicit val format = jsonFormat(Test)("a".withMissingFieldAt(24))

        JsObject().as[Test] must be_==(Test(24))
        Test(24).toJson must be_==(JsObject())

        JsObject("a" -> JsNumber(39)).as[Test] must be_==(Test(39))
        Test(39).toJson must be_==(JsObject("a" -> JsNumber(39)))
      }
      "by setting defaults for possibly missing elements" in {
        implicit val format = jsonFormat(Test)("a".withDefault(12))

        JsObject().as[Test] must be_==(Test(12))
        Test(12).toJson must be_==(JsObject("a" -> JsNumber(12)))
      }
      "by producing additional json ouput" in {
        implicit val format =
          jsonFormat(Test)("a".as[String].using(_.toInt, _.toString))
            .extraField("b", _.a + 5)

        Test(5).toJson must be_==(JsObject("a" -> JsString("5"), "b" -> JsNumber(10)))
      }
    }
  }
}
