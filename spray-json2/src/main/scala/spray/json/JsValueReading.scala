package spray.json

trait JsValueReading {
  def toValidated[A :JsonReader]: Validated[A]

  def apply(fieldName: String): Validated[JsValue]
  def apply(idx: Int): Validated[JsValue]

  def as[A: JsonReader]: A = toValidated[A].get
  def toOption[A :JsonReader]: Option[A] = toValidated[A].toOption
  def toEither[A :JsonReader]: Either[Throwable, A] = toValidated[A].toEither
}

trait JsValueReadingImplicits {
  implicit def canReadFromValue(value: JsValue): JsValueReading =
    new JsValueReading {
      def toValidated[A: JsonReader]: Validated[A] = jsonReader[A].read(value)

      import lenses.JsonLenses.{value => _, _}
      def apply(fieldName: String): Validated[JsValue] = field(fieldName).tryGet[JsValue](value)
      def apply(idx: Int): Validated[JsValue] = element(idx).tryGet[JsValue](value)
    }
  implicit def canReadFromDynValue(value: DynamicJsValue): JsValueReading =
    canReadFromValue(value._value)

  implicit def canReadFromValidatedValue(value: Validated[JsValue]): JsValueReading =
    new JsValueReading {
      def toValidated[A: JsonReader]: Validated[A] =
        value.flatMap(jsonReader[A].read)

      import lenses.JsonLenses.{value => _, _}
      def apply(fieldName: String): Validated[JsValue] =
        value.flatMap(field(fieldName).tryGet[JsValue])
      def apply(idx: Int): Validated[JsValue] =
        value.flatMap(element(idx).tryGet[JsValue])
    }
  implicit def canReadFromDynValidatedValue(value: DynamicValidatedJsValue): JsValueReading =
    canReadFromValidatedValue(value.value)
}
