package spray.json

trait OptionFormats extends LowLevelOptionFormats {
  /**
   * Always use `OptionFormatAsArray` per default for nested options. Use the low-level
   * optionFormat for everything else.
   */
  implicit def nestedOptionFormat[T](implicit inner: JsonFormat[Option[T]]): JsonFormat[Option[Option[T]]] =
    new OptionFormatAsArray[Option[T]]
}

trait LowLevelOptionFormats {
  implicit def optionFormat[T](implicit tFormat: JsonFormat[T], leafLevelStrategy: LeafLevelOptionFormat): JsonFormat[Option[T]] =
    leafLevelStrategy.get[T]
}

/**
 * Defines how a leafLevel options (i.e. not nested options) should
 * be serialized. The default is as self/null. Bring `LeafLevelOptionFormat.asArray` or a
 * custom format in scope to use this one.
 */
trait LeafLevelOptionFormat {
  def get[T: JsonFormat]: JsonFormat[Option[T]]
}

object LeafLevelOptionFormat {
  implicit def asSelf: LeafLevelOptionFormat =
    new LeafLevelOptionFormat {
      def get[T: JsonFormat]: JsonFormat[Option[T]] = new OptionFormatAsSelf[T]
    }

  def asArray: LeafLevelOptionFormat =
    new LeafLevelOptionFormat {
      def get[T: JsonFormat]: JsonFormat[Option[T]] = new OptionFormatAsArray[T]
    }
}

/**
 * Format for values of type `Option[T]`. Encodes `Some(x)` as
 * `x.toJson` and `None` as `JsNull`. OptionFormats of this type
 * can't be wrapped without losing information. Use `OptionFormatAsArray`
 * for nested Option cases.
 */
class OptionFormatAsSelf[T: JsonFormat] extends JsonFormat[Option[T]] {
  def write(option: Option[T]) = option match {
    case Some(x) ⇒ x.toJson
    case None    ⇒ JsNull
  }
  def read(value: JsValue) = value match {
    case JsNull ⇒ Success(None)
    case x      ⇒ x.toValidated[T].map(Some(_))
  }
}

/**
 * Format values of type `Option[T]`. Encodes `Some(x)` as `JsArray(x.toJson)`
 * and `None` as `JsArray.empty`.
 */
class OptionFormatAsArray[T: JsonFormat] extends JsonFormat[Option[T]] {
  def write(option: Option[T]) = option match {
    case Some(x) ⇒ JsArray(x.toJson)
    case None    ⇒ JsArray.empty
  }
  def read(value: JsValue) = value match {
    case JsArray(Seq())  ⇒ Success(None)
    case JsArray(Seq(x)) ⇒ x.toValidated[T].map(Some(_))
    case x               ⇒ deserializationError("Expected Option as JsArray, but got " + x)
  }
}
