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

package cc.spray

trait SimpleParser[A] extends (String => Either[String, A])

trait SimpleParsers {
  implicit object SimpleStringParser extends SimpleParser[String] {
    def apply(value: String) = Right(value)
  }

  implicit object SimpleSymbolParser extends SimpleParser[Symbol] {
    def apply(value: String) = Right(Symbol(value))
  }

  implicit object SimpleIntParser extends SimpleParser[Int] {
    def apply(value: String) = {
      try {
        Right(value.toInt)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit integer value") 
      }
    }
  }
  
  object HexInt extends SimpleParser[Int] {
    def apply(value: String) = {
      try {
        Right(Integer.parseInt(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit object SimpleLongParser extends SimpleParser[Long] {
    def apply(value: String) = {
      try {
        Right(value.toLong)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit integer value") 
      }
    }
  }
  
  object HexLong extends SimpleParser[Long] {
    def apply(value: String) = {
      try {
        Right(java.lang.Long.parseLong(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit object SimpleDoubleParser extends SimpleParser[Double] {
    def apply(value: String) = {
      try {
        Right(value.toDouble)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value") 
      }
    }
  }

  implicit object SimpleFloatParser extends SimpleParser[Float] {
    def apply(value: String) = {
      try {
        Right(value.toFloat)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value")
      }
    }
  }

  implicit object SimpleShortParser extends SimpleParser[Short] {
    def apply(value: String) = {
      try {
        Right(value.toShort)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 16-bit integer value")
      }
    }
  }

  implicit object SimpleByteParser extends SimpleParser[Byte] {
    def apply(value: String) = {
      try {
        Right(value.toByte)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 8-bit integer value")
      }
    }
  }
  
  implicit object SimpleBooleanParser extends SimpleParser[Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left("'" + x + "' is not a valid Boolean value")
    }
  }  
}

object SimpleParsers extends SimpleParsers