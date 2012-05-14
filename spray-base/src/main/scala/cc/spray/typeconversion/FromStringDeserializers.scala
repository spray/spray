/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray
package typeconversion

trait FromStringDeserializers {

  implicit val String2SymbolConverter = new Deserializer[String, Symbol] {
    def apply(value: String) = Right(Symbol(value))
  }

  implicit val String2IntConverter = new Deserializer[String, Int] {
    def apply(value: String) = {
      try {
        Right(value.toInt)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid 32-bit integer value"))
      }
    }
  }

  object HexInt extends Deserializer[String, Int] {
    def apply(value: String) = {
      try {
        Right(Integer.parseInt(value, 16))
      } catch {
        case _: NumberFormatException =>
          Left(MalformedContent("'" + value + "' is not a valid 32-bit hexadecimal integer value"))
      }
    }
  }

  implicit val String2LongConverter = new Deserializer[String, Long] {
    def apply(value: String) = {
      try {
        Right(value.toLong)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid 64-bit integer value"))
      }
    }
  }

  object HexLong extends Deserializer[String, Long] {
    def apply(value: String) = {
      try {
        Right(java.lang.Long.parseLong(value, 16))
      } catch {
        case _: NumberFormatException =>
          Left(MalformedContent("'" + value + "' is not a valid 64-bit hexadecimal integer value"))
      }
    }
  }

  implicit val String2DoubleConverter = new Deserializer[String, Double] {
    def apply(value: String) = {
      try {
        Right(value.toDouble)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid floating point value"))
      }
    }
  }

  implicit val String2FloatConverter = new Deserializer[String, Float] {
    def apply(value: String) = {
      try {
        Right(value.toFloat)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid floating point value"))
      }
    }
  }

  implicit val String2ShortConverter = new Deserializer[String, Short] {
    def apply(value: String) = {
      try {
        Right(value.toShort)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid 16-bit integer value"))
      }
    }
  }

  implicit val String2ByteConverter = new Deserializer[String, Byte] {
    def apply(value: String) = {
      try {
        Right(value.toByte)
      } catch {
        case _: NumberFormatException => Left(MalformedContent("'" + value + "' is not a valid 8-bit integer value"))
      }
    }
  }

  implicit val String2BooleanConverter = new Deserializer[String, Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left(MalformedContent("'" + x + "' is not a valid Boolean value"))
    }
  }
}

object FromStringDeserializers extends FromStringDeserializers










