package cc.spray
package directives

private[spray] trait ParameterConverters {

  implicit object StringParameterConverter extends ParameterConverter[String] {
    def apply(value: String) = Right(value)
  }

  implicit object SymbolParameterConverter extends ParameterConverter[Symbol] {
    def apply(value: String) = Right(Symbol(value))
  }

  implicit object IntParameterConverter extends ParameterConverter[Int] {
    def apply(value: String) = {
      try {
        Right(value.toInt)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit integer value") 
      }
    }
  }
  
  object HexInt extends ParameterConverter[Int] {
    def apply(value: String) = {
      try {
        Right(Integer.parseInt(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit object LongParameterConverter extends ParameterConverter[Long] {
    def apply(value: String) = {
      try {
        Right(value.toLong)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit integer value") 
      }
    }
  }
  
  object HexLong extends ParameterConverter[Long] {
    def apply(value: String) = {
      try {
        Right(java.lang.Long.parseLong(value, 16))
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 64-bit hexadecimal integer value") 
      }
    }
  }
  
  implicit object DoubleParameterConverter extends ParameterConverter[Double] {
    def apply(value: String) = {
      try {
        Right(value.toDouble)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value") 
      }
    }
  }

  implicit object FloatParameterConverter extends ParameterConverter[Float] {
    def apply(value: String) = {
      try {
        Right(value.toFloat)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid floating point value")
      }
    }
  }

  implicit object ShortParameterConverter extends ParameterConverter[Short] {
    def apply(value: String) = {
      try {
        Right(value.toShort)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 16-bit integer value")
      }
    }
  }

  implicit object ByteParameterConverter extends ParameterConverter[Byte] {
    def apply(value: String) = {
      try {
        Right(value.toByte)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 8-bit integer value")
      }
    }
  }
  
  implicit object BooleanParameterConverter extends ParameterConverter[Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left("'" + x + "' is not a valid Boolean value")
    }
  }  
}