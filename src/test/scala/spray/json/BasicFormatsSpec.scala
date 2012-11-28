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

import org.specs2.mutable._

class BasicFormatsSpec extends Specification {

  "The IntJsonFormat" should {
    "convert an Int to a JsNumber" in {
      42.toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to an Int" in {
      JsNumber(42).as[Int] mustEqual 42
    }
  }

  "The LongJsonFormat" should {
    "convert a Long to a JsNumber" in {
      42L.toJson mustEqual JsNumber(42L)
    }
    "convert a JsNumber to a Long" in {
      JsNumber(42L).as[Long] mustEqual 42L
    }
  }

  "The FloatJsonFormat" should {
    "convert a Float to a JsNumber" in {
      4.2f.toJson mustEqual JsNumber(4.2f)
    }
    "convert a Float.NaN to a JsNull" in {
      Float.NaN.toJson mustEqual JsNull
    }
    "convert a Float.PositiveInfinity to a JsNull" in {
      Float.PositiveInfinity.toJson mustEqual JsNull
    }
    "convert a Float.NegativeInfinity to a JsNull" in {
      Float.NegativeInfinity.toJson mustEqual JsNull
    }
    "convert a JsNumber to a Float" in {
      JsNumber(4.2f).as[Float] mustEqual 4.2f
    }
    "convert a JsNull to a Float" in {
      JsNull.as[Float].isNaN mustEqual Float.NaN.isNaN
    }
  }

  "The DoubleJsonFormat" should {
    "convert a Double to a JsNumber" in {
      4.2.toJson mustEqual JsNumber(4.2)
    }
    "convert a Double.NaN to a JsNull" in {
      Double.NaN.toJson mustEqual JsNull
    }
    "convert a Double.PositiveInfinity to a JsNull" in {
      Double.PositiveInfinity.toJson mustEqual JsNull
    }
    "convert a Double.NegativeInfinity to a JsNull" in {
      Double.NegativeInfinity.toJson mustEqual JsNull
    }
    "convert a JsNumber to a Double" in {
      JsNumber(4.2).as[Double] mustEqual 4.2
    }
    "convert a JsNull to a Double" in {
      JsNull.as[Double].isNaN mustEqual Double.NaN.isNaN
    }
  }

  "The ByteJsonFormat" should {
    "convert a Byte to a JsNumber" in {
      42.asInstanceOf[Byte].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Byte" in {
      JsNumber(42).as[Byte] mustEqual 42
    }
  }

  "The ShortJsonFormat" should {
    "convert a Short to a JsNumber" in {
      42.asInstanceOf[Short].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Short" in {
      JsNumber(42).as[Short] mustEqual 42
    }
  }

  "The BigDecimalJsonFormat" should {
    "convert a BigDecimal to a JsNumber" in {
      BigDecimal(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigDecimal" in {
      JsNumber(42).as[BigDecimal] mustEqual BigDecimal(42)
    }
  }

  "The BigIntJsonFormat" should {
    "convert a BigInt to a JsNumber" in {
      BigInt(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigInt" in {
      JsNumber(42).as[BigInt] mustEqual BigInt(42)
    }
  }

  "The UnitJsonFormat" should {
    "convert Unit to a JsNumber(1)" in {
      ().toJson mustEqual JsNumber(1)
    }
    "convert a JsNumber to Unit" in {
      JsNumber(1).as[Unit] mustEqual ()
    }
  }

  "The BooleanJsonFormat" should {
    "convert true to a JsTrue" in { true.toJson mustEqual JsTrue }
    "convert false to a JsFalse" in { false.toJson mustEqual JsFalse }
    "convert a JsTrue to true" in { JsTrue.as[Boolean] mustEqual true }
    "convert a JsFalse to false" in { JsFalse.as[Boolean] mustEqual false }
  }

  "The CharJsonFormat" should {
    "convert a Char to a JsString" in {
      'c'.toJson mustEqual JsString("c")
    }
    "convert a JsString to a Char" in {
      JsString("c").as[Char] mustEqual 'c'
    }
  }

  "The StringJsonFormat" should {
    "convert a String to a JsString" in {
      "Hello".toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a String" in {
      JsString("Hello").as[String] mustEqual "Hello"
    }
  }

  "The SymbolJsonFormat" should {
    "convert a Symbol to a JsString" in {
      'Hello.toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a Symbol" in {
      JsString("Hello").as[Symbol] mustEqual 'Hello
    }
  }

}
