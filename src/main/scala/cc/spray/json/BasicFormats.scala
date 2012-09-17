/*
 * Original implementation (C) 2009-2011 Debasish Ghosh
 * Adapted and extended in 2011 by Mathias Doenitz
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

/**
  * Provides the JsonFormats for the most important Scala types.
 */
trait BasicFormats {
  implicit lazy val ByteJsonFormat: JsonFormat[Byte] =
    new NumberFormat[Byte](_.byteValue(), JsNumber(_))

  implicit lazy val ShortJsonFormat: JsonFormat[Short] =
    new NumberFormat[Short](_.shortValue(), JsNumber(_))

  implicit lazy val IntJsonFormat: JsonFormat[Int] =
    new NumberFormat[Int](_.intValue(), JsNumber.apply)

  implicit lazy val LongJsonFormat: JsonFormat[Long] =
    new NumberFormat[Long](_.longValue(), JsNumber.apply)

  implicit lazy val BigDecimalJsonFormat: JsonFormat[BigDecimal] =
    new NumberFormat[BigDecimal](identity, JsNumber.apply)

  implicit lazy val BigIntJsonFormat: JsonFormat[BigInt] =
    new NumberFormat[BigInt](_.toBigInt(), JsNumber.apply)

  class NumberFormat[T: ClassManifest](reader: BigDecimal => T, writer: T => JsNumber) extends JsonFormat[T] {
    val typeName = classManifest[T].erasure.getSimpleName

    def write(obj: T): JsValue = writer(obj)
    def read(json: JsValue): Validated[T] = json match {
      case x: JsNumber => Success(reader(x.value))
      case x => deserializationError("Expected %s as JsNumber, but got %s" format (typeName, x))
    }
  }

  implicit lazy val FloatJsonFormat: JsonFormat[Float] = new JsonFormat[Float] {
    def write(x: Float) = JsNumber(x)
    def read(value: JsValue) = value match {
      case x: JsNumber => Success(x.value.floatValue)
      case JsNull      => Success(Float.NaN)
      case x => deserializationError("Expected Float as JsNumber, but got " + x)
    }
  }

  implicit lazy val DoubleJsonFormat: JsonFormat[Double] = new JsonFormat[Double] {
    def write(x: Double) = JsNumber(x)
    def read(value: JsValue) = value match {
      case x: JsNumber => Success(x.value.doubleValue)
      case JsNull      => Success(Double.NaN)
      case x => deserializationError("Expected Double as JsNumber, but got " + x)
    }
  }

  implicit lazy val UnitJsonFormat: JsonFormat[Unit] = new JsonFormat[Unit] {
    def write(x: Unit) = JsNumber(1)
    def read(value: JsValue) = value match {
      case x: JsNumber if x.value == 1 => Success(())
      case x => deserializationError("Expected Unit as JsNumber(1), but got " + x)
    }
  }

  implicit lazy val BooleanJsonFormat: JsonFormat[Boolean] = new JsonFormat[Boolean] {
    def write(x: Boolean) = JsBoolean(x)
    def read(value: JsValue) = value match {
      case JsTrue => Success(true)
      case JsFalse => Success(false)
      case x => deserializationError("Expected JsBoolean, but got " + x)
    }
  }

  implicit lazy val CharJsonFormat: JsonFormat[Char] = new JsonFormat[Char] {
    def write(x: Char) = JsString(String.valueOf(x))
    def read(value: JsValue) = value match {
      case x: JsString if x.value.length == 1 => Success(x.value.charAt(0))
      case x => deserializationError("Expected Char as single-character JsString, but got " + x)
    }
  }

  implicit lazy val StringJsonFormat: JsonFormat[String] = new JsonFormat[String] {
    def write(x: String) = JsString(x)
    def read(value: JsValue) = value match {
      case x: JsString => Success(x.value)
      case x => deserializationError("Expected String as JsString, but got " + x)
    }
  }

  implicit lazy val SymbolJsonFormat: JsonFormat[Symbol] = new JsonFormat[Symbol] {
    def write(x: Symbol) = JsString(x.name)
    def read(value: JsValue) = value match {
      case x: JsString => Success(Symbol(x.value))
      case x => deserializationError("Expected Symbol as JsString, but got " + x)
    }
  }

}
