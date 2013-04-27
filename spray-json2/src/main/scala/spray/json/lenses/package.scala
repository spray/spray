package spray.json

package object lenses {
  type JsPred = JsValue ⇒ Boolean
  type Id[T] = T
  type Reader[T] = JsonReader[T]
  type SafeJsValue = Validated[JsValue]

  type Operation = SafeJsValue ⇒ SafeJsValue

  type ScalarLens = Lens[Id]
  type OptLens = Lens[Option]
  type SeqLens = Lens[Seq]

  def ??? = sys.error("NYI")
  def unexpected[T](message: String): Validated[T] = Failure(new RuntimeException(message))
  def outOfBounds[T](message: String): Validated[T] = Failure(new IndexOutOfBoundsException(message))

  case class ValidateOption[T](option: Option[T]) {
    def getOrError(message: ⇒ String): Validated[T] = option match {
      case Some(t) ⇒ Success(t)
      case None    ⇒ unexpected(message)
    }
  }

  implicit def validateOption[T](o: Option[T]): ValidateOption[T] = ValidateOption(o)
}