package cc.spray.json

package object lenses {
  type JsPred = JsValue => Boolean
  type Id[T] = T
  type Validated[T] = Either[Exception, T]
  type SafeJsValue = Validated[JsValue]

  type Operation = SafeJsValue => SafeJsValue

  type ScalarLens = Lens[Id]
  type OptLens = Lens[Option]
  type SeqLens = Lens[Seq]

  def ??? = sys.error("NYI")
  def unexpected(message: String) = Left(new RuntimeException(message))
  def outOfBounds(message: String) = Left(new IndexOutOfBoundsException(message))

  implicit def rightBiasEither[A, B](e: Either[A, B]): Either.RightProjection[A, B] = e.right

  case class GetOrThrow[B](e: Either[Throwable, B]) {
    def getOrThrow: B = e match {
      case Right(b) => b
      case Left(e) => throw e
    }
  }

  implicit def orThrow[B](e: Either[Throwable, B]): GetOrThrow[B] = GetOrThrow(e)

  trait Reader[T] {
    def read(js: JsValue): Validated[T]
  }

  object Reader {
    implicit def safeMonadicReader[T: JsonReader]: Reader[T] = new Reader[T] {
      def read(js: JsValue): Validated[T] =
        safe(js.convertTo[T])
    }
  }

  def safe[T](body: => T): Validated[T] =
    try {
      Right(body)
    } catch {
      case e: Exception => Left(e)
    }

  case class ValidateOption[T](option: Option[T]) {
    def getOrError(message: => String): Validated[T] = option match {
      case Some(t) => Right(t)
      case None => unexpected(message)
    }
  }

  implicit def validateOption[T](o: Option[T]): ValidateOption[T] = ValidateOption(o)
}