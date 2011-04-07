package cc.spray
package builders

private[spray] trait ParameterConverters {
  this: ParameterBuilders =>
  
  implicit object IntParameterConverter extends ParameterConverter[Int] {
    def apply(value: String) = {
      try {
        Right(value.toInt)
      } catch {
        case _: NumberFormatException => Left("'" + value + "' is not a valid 32-bit integer value") 
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
  
  implicit object BooleanParameterConverter extends ParameterConverter[Boolean] {
    def apply(value: String) = value.toLowerCase match {
      case "true" | "yes" | "on" => Right(true)
      case "false" | "no" | "off" => Right(false)
      case x => Left("'" + x + "' is not a valid Boolean value")
    }
  }
}