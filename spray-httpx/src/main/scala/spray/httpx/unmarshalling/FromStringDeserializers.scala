/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.httpx.unmarshalling


trait FromStringDeserializers {

  implicit val String2SymbolConverter = new FromStringDeserializer[Symbol] {
    def apply(value: String) = Right(Symbol(value))
  }

  implicit val String2IntConverter = new FromStringDeserializer[Int] {
    def apply(value: String) = {
      try Right(value.toInt)
      catch numberFormatError(value, "32-bit integer")
    }
  }

  object HexInt extends FromStringDeserializer[Int] {
    def apply(value: String) = {
      try Right(Integer.parseInt(value, 16))
      catch numberFormatError(value, "32-bit hexadecimal integer")
    }
  }

  implicit val String2LongConverter = new FromStringDeserializer[Long] {
    def apply(value: String) = {
      try Right(value.toLong)
      catch numberFormatError(value, "64-bit integer")
    }
  }

  object HexLong extends FromStringDeserializer[Long] {
    def apply(value: String) = {
      try Right(java.lang.Long.parseLong(value, 16))
      catch numberFormatError(value, "64-bit hexadecimal integer")
    }
  }

  implicit val String2DoubleConverter = new FromStringDeserializer[Double] {
    def apply(value: String) = {
      try Right(value.toDouble)
      catch numberFormatError(value, "floating point")
    }
  }

  implicit val String2FloatConverter = new FromStringDeserializer[Float] {
    def apply(value: String) = {
      try Right(value.toFloat)
      catch numberFormatError(value, "floating point")
    }
  }

  implicit val String2ShortConverter = new FromStringDeserializer[Short] {
    def apply(value: String) = {
      try Right(value.toShort)
      catch numberFormatError(value, "16-bit integer")
    }
  }

  implicit val String2ByteConverter = new FromStringDeserializer[Byte] {
    def apply(value: String) = {
      try Right(value.toByte)
      catch numberFormatError(value, "8-bit integer")
    }
  }

  private def numberFormatError(value: String,
                                target: String): PartialFunction[Throwable, Either[DeserializationError, Nothing]] = {
    case e: NumberFormatException =>
      Left(MalformedContent("'%s' is not a valid %s value" format (value, target), e))
  }

  implicit val String2BooleanConverter = new FromStringDeserializer[Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left(MalformedContent("'" + x + "' is not a valid Boolean value"))
    }
  }
}

object FromStringDeserializers extends FromStringDeserializers
