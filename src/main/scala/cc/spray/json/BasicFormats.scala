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

package spray.json

/**
  * Provides the JsonFormats for the most important Scala types.
 */
trait BasicFormats {

  implicit object IntJsonFormat extends JsonFormat[Int] {
    def write(x: Int) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.intValue
      case x => deserializationError("Expected Int as JsNumber, but got " + x)
    }
  }

  implicit object LongJsonFormat extends JsonFormat[Long] {
    def write(x: Long) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.longValue
      case x => deserializationError("Expected Long as JsNumber, but got " + x)
    }
  }

  implicit object FloatJsonFormat extends JsonFormat[Float] {
    def write(x: Float) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.floatValue
      case JsNull      => Float.NaN
      case x => deserializationError("Expected Float as JsNumber, but got " + x)
    }
  }

  implicit object DoubleJsonFormat extends JsonFormat[Double] {
    def write(x: Double) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.doubleValue
      case JsNull      => Double.NaN
      case x => deserializationError("Expected Double as JsNumber, but got " + x)
    }
  }

  implicit object ByteJsonFormat extends JsonFormat[Byte] {
    def write(x: Byte) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.byteValue
      case x => deserializationError("Expected Byte as JsNumber, but got " + x)
    }
  }
  
  implicit object ShortJsonFormat extends JsonFormat[Short] {
    def write(x: Short) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.shortValue
      case x => deserializationError("Expected Short as JsNumber, but got " + x)
    }
  }

  implicit object BigDecimalJsonFormat extends JsonFormat[BigDecimal] {
    def write(x: BigDecimal) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x
      case x => deserializationError("Expected BigDecimal as JsNumber, but got " + x)
    }
  }

  implicit object BigIntJsonFormat extends JsonFormat[BigInt] {
    def write(x: BigInt) = JsNumber(x)
    def read(value: JsValue) = value match {
      case JsNumber(x) => x.toBigInt
      case x => deserializationError("Expected BigInt as JsNumber, but got " + x)
    }
  }

  implicit object UnitJsonFormat extends JsonFormat[Unit] {
    def write(x: Unit) = JsNumber(1)
    def read(value: JsValue) {}
  }

  implicit object BooleanJsonFormat extends JsonFormat[Boolean] {
    def write(x: Boolean) = JsBoolean(x)
    def read(value: JsValue) = value match {
      case JsTrue => true
      case JsFalse => false
      case x => deserializationError("Expected JsBoolean, but got " + x)
    }
  }

  implicit object CharJsonFormat extends JsonFormat[Char] {
    def write(x: Char) = JsString(String.valueOf(x))
    def read(value: JsValue) = value match {
      case JsString(x) if x.length == 1 => x.charAt(0)
      case x => deserializationError("Expected Char as single-character JsString, but got " + x)
    }
  }
  
  implicit object StringJsonFormat extends JsonFormat[String] {
    def write(x: String) = JsString(x)
    def read(value: JsValue) = value match {
      case JsString(x) => x
      case x => deserializationError("Expected String as JsString, but got " + x)
    }
  }
  
  implicit object SymbolJsonFormat extends JsonFormat[Symbol] {
    def write(x: Symbol) = JsString(x.name)
    def read(value: JsValue) = value match {
      case JsString(x) => Symbol(x)
      case x => deserializationError("Expected Symbol as JsString, but got " + x)
    }
  }
  
}
