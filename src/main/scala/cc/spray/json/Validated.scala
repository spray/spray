package cc.spray.json

object Validated {
  def apply[T](body: => T): Validated[T] = {
    try {
      Success(body)
    } catch {
      case ex => Failure(ex)
    }
  }
}

sealed abstract class Validated[+T] extends Dynamic {
  def isSuccess: Boolean
  def isFailure: Boolean

  def get: T
  def getOrElse[R >: T](default: => R): R
  def orElse[R >: T](alternative: => Validated[R]): Validated[R]

  def toEither: Either[Throwable, T]
  def toOption: Option[T]

  def map[R](f: T => R): Validated[R]
  def flatMap[R](f: T => Validated[R]): Validated[R]
  def foreach(f: T => Unit)
  def filter(p: T => Boolean): Validated[T]

  // special features that are only available to instances of Validated[JsValue]
  def as[A](implicit jfa: JsonFormat[A], ev: T <:< JsValue): A = get.as[A]
  def applyDynamic(key: String)(index: Int = -1)(implicit ev: T <:< JsValue): Validated[JsValue] = flatMap(_.applyDynamic(key)(index))
  def apply(index: Int)(implicit ev: T <:< JsValue): Validated[JsValue] = flatMap(_.apply(index))
  def apply(key: String)(implicit ev: T <:< JsValue): Validated[JsValue] = flatMap(_.apply(key))
}

case class Failure[+T](throwable: Throwable) extends Validated[T] {
  def isSuccess = false
  def isFailure = true

  def get = throw throwable
  def getOrElse[R >: T](default: => R) = default
  def orElse[R >: T](alternative: => Validated[R]) = alternative

  def toEither = Left(throwable)
  def toOption = None

  def map[R](f: T => R) = this.asInstanceOf[Validated[R]]
  def flatMap[R](f: T => Validated[R]) = this.asInstanceOf[Validated[R]]
  def foreach(f: T => Unit) {}
  def filter(p: T => Boolean) = this
}

case class Success[+T](value: T) extends Validated[T] {
  def isSuccess = true
  def isFailure = false

  def get = value
  def getOrElse[R >: T](default: => R) = value
  def orElse[R >: T](alternative: => Validated[R]) = this

  def toEither = Right(value)
  def toOption = Some(value)

  def map[R](f: T => R) = Validated(f(value))
  def flatMap[R](f: T => Validated[R]) = {
    try {
      f(value)
    } catch {
      case ex => Failure(ex)
    }
  }
  def foreach(f: T => Unit) { f(value) }
  def filter(p: T => Boolean) = {
    try {
      if (p(value)) this
      else Failure(new UnsatisfiedFilterException())
    } catch {
      case ex => Failure(ex)
    }
  }
}

class UnsatisfiedFilterException(msg: String = "") extends RuntimeException(msg)