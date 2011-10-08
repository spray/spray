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

trait TypeConverter[A, B] extends (A => Either[String, B])

object TypeConverter {
  // implemented as an optimization; we could get away without an explicit IdentityConverter since the
  // fromFunctionConverter below makes identity conversion available through the Predef.conforms implicit conversion,
  // however, the explicit IdentityConverter saves the construction of a new TypeConverter object for identity conversions
  private val IdentityConverter = new TypeConverter[Any, Any] { def apply(obj: Any) = Right(obj) }
  implicit def identityConverter[A] = IdentityConverter.asInstanceOf[TypeConverter[A, A]]

  implicit def fromFunctionConverter[A, B](implicit f: A => B) = new TypeConverter[A, B] {
    def apply(a: A) = {
      try {
        Right(f(a))
      } catch {
        case ex => Left(ex.toString)
      }
    }
  }
}

trait TypeConverters {

  implicit val String2SymbolConverter = new TypeConverter[String, Symbol] {
    def apply(value: String) = Right(Symbol(value))
  }

  implicit val String2IntConverter = new TypeConverter[String, Int] {
    def apply(value: String) = {
      try {
        Right(value.toInt)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit integer value") 
      }
    }
  }
  
  object HexInt extends TypeConverter[String, Int] {
    def apply(value: String) = {
      try {
        Right(Integer.parseInt(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit val String2LongConverter = new TypeConverter[String, Long] {
    def apply(value: String) = {
      try {
        Right(value.toLong)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit integer value") 
      }
    }
  }
  
  object HexLong extends TypeConverter[String, Long] {
    def apply(value: String) = {
      try {
        Right(java.lang.Long.parseLong(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit val String2DoubleConverter = new TypeConverter[String, Double] {
    def apply(value: String) = {
      try {
        Right(value.toDouble)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value") 
      }
    }
  }

  implicit val String2FloatConverter = new TypeConverter[String, Float] {
    def apply(value: String) = {
      try {
        Right(value.toFloat)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value")
      }
    }
  }

  implicit val String2ShortConverter = new TypeConverter[String, Short] {
    def apply(value: String) = {
      try {
        Right(value.toShort)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 16-bit integer value")
      }
    }
  }

  implicit val String2ByteConverter = new TypeConverter[String, Byte] {
    def apply(value: String) = {
      try {
        Right(value.toByte)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 8-bit integer value")
      }
    }
  }
  
  implicit val String2BooleanConverter = new TypeConverter[String, Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left("'" + x + "' is not a valid Boolean value")
    }
  }  
}

object TypeConverters extends TypeConverters