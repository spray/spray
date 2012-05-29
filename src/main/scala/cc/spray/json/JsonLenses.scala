package cc.spray.json

object JsonLenses {
  type JsPred = JsValue => Boolean
  type Id[T] = T
  type Validated[T] = Either[Exception, T]
  type SafeJsValue = Validated[JsValue]

  implicit def rightBiasEither[A, B](e: Either[A, B]): Either.RightProjection[A, B] = e.right

  case class GetOrThrow[B](e: Either[Throwable, B]) {
    def getOrThrow: B = e match {
      case Right(b) => b
      case Left(e) => throw e
    }
  }
  implicit def orThrow[B](e: Either[Throwable, B]): GetOrThrow[B] = GetOrThrow(e)

  trait MonadicReader[T] {
    def read(js: JsValue): Validated[T]
  }
  def safe[T](body: => T): Validated[T] =
    try {
      Right(body)
    } catch {
      case e: Exception => Left(e)
    }

  implicit def safeReader[T: JsonReader]: MonadicReader[T] = new MonadicReader[T] {
    def read(js: JsValue): Validated[T] =
      safe(js.convertTo[T])
  }

  case class ValidateOption[T](option: Option[T]) {
    def getOrError(message: => String): Validated[T] = option match {
      case Some(t) => Right(t)
      case None => unexpected(message)
    }
  }
  implicit def validateOption[T](o: Option[T]): ValidateOption[T] = ValidateOption(o)

  trait Update {
    def apply(value: JsValue): JsValue

    def &&(next: Update): Update = ???
  }

  implicit def strToField(name: String): ScalarProjection = field(name)

  trait Extractor[T] {
    def apply[M[_]](p: Projection[M]): M[T]
  }

  trait A {
    type X[_]
  }
  case class RichJsValue(value: JsValue) {
    def update(updater: Update): JsValue = updater(value)
    def update[T: JsonWriter, M[_]](proj: Projection[M], pValue: T): JsValue = proj ! set(pValue) apply value
    def extract[T](f: JsValue => T): T = f(value)
    def extract[T: MonadicReader]: Extractor[T] = new Extractor[T] {
      def apply[M[_]](p: Projection[M]): M[T] = p.get[T] apply value
    }
    def apply[T: MonadicReader](p: Projection[Id]): T = p.get[T] apply value

    def as[T: MonadicReader]: Validated[T] =
      implicitly[MonadicReader[T]].read(value)
  }

  implicit def updatable(value: JsValue): RichJsValue = RichJsValue(value)

  trait Operation {
    def apply(value: SafeJsValue): SafeJsValue
  }

  trait Updateable {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue
  }
  trait Projection[M[_]] extends Updateable {
    type ThenScalar
    def retr: JsValue => Validated[M[JsValue]]
    def mapValue[T](value: M[JsValue])(f: JsValue => Validated[T]): Validated[M[T]]

    def getSecure[T: MonadicReader]: JsValue => Validated[M[T]]
    def get[T: MonadicReader]: JsValue => M[T]
    def ![U](op: Operation): Update

    //def is(f: M[T] => Boolean): JsPred
    def is[U: MonadicReader](f: U => Boolean): JsPred

    def andThen(next: ScalarProjection): ThenScalar

    def /(fieldName: String) = this andThen fieldName
    def apply(idx: Int) = element(idx)
    def element(idx: Int) = this andThen JsonLenses.element(idx)

    //def /[M2[_], R[_]](next: Projection[M2])(implicit conv: Conv[M, M2, R]): Projection[R]
  }

  /*
  trait Conv[M[_], M2[_], R[_]] {
    //def flatMap[T](first: M[T], second: M2[T])(op: (T, T) => T): R[T]
  }
  object Conv {
    implicit def joinScalar[M2[_]]: Conv[Id, M2, M2] = new Conv[Id, M2, M2] {
      def flatMap[T](first: T, second: M2[T])(op: (T, T) => T): M2[T] = ???.asInstanceOf[M2[T]]

    }
    implicit def joinSeq[M2[_]]: Conv[Seq, M2, Seq] = ???
    implicit def joinOptId: Conv[Option, Id, Option] = ???
    implicit def joinOptOpt: Conv[Option, Option, Option] = ???
    implicit def joinOptSeq: Conv[Option, Seq, Seq] = ???
  }*/
  trait ScalarProjection extends Projection[Id] {
    type ThenScalar = ScalarProjection

    def andThen(next: ScalarProjection): ScalarProjection
    def andThen(next: OptProjection): OptProjection
  }
  trait OptProjection extends Projection[Option] {
    type ThenScalar = OptProjection

    def andThen(next: ScalarProjection): OptProjection
    def andThen(next: OptProjection): OptProjection
  }

  type SeqProjection = Projection[Seq]

  trait ProjectionImpl[M[_]] extends Projection[M] { outer =>
    def getSecure[T: MonadicReader]: JsValue => Validated[M[T]] =
      p => retr(p).flatMap(mapValue(_)(_.as[T]))

    def get[T: MonadicReader]: JsValue => M[T] =
      p => getSecure[T].apply(p).getOrThrow

    def ![U](op: Operation): Update = new Update {
      def apply(parent: JsValue): JsValue =
        updated(op(_))(parent).getOrThrow
    }

    abstract class Joined(next: Updateable) extends Updateable {
      def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
        outer.updated(_.flatMap(next.updated(f)))(parent)
    }
  }

  trait ScalarProjectionImpl extends ScalarProjection with ProjectionImpl[Id] { outer =>
    def mapValue[T](value: JsValue)(f: JsValue => Validated[T]): Validated[T] =
      f(value)

    def is[U: MonadicReader](f: U => Boolean): JsPred =
      value => getSecure[U] apply value exists f

    def andThen(next: ScalarProjection): ScalarProjection =
      new Joined(next) with ScalarProjectionImpl {
        def retr: JsValue => SafeJsValue = parent =>
          for {
            outerV <- outer.retr(parent)
            innerV <- next.retr(outerV)
          } yield innerV
      }

    def andThen(next: OptProjection): OptProjection = new Joined(next) with OptProjectionImpl {
      def retr: JsValue => Validated[Option[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- next.retr(outerV)
        } yield innerV
    }
  }

  trait OptProjectionImpl extends OptProjection with ProjectionImpl[Option] { outer =>
    def mapValue[T](value: Option[JsValue])(f: JsValue => Validated[T]): Validated[Option[T]] =
      swap(value.map(f))

    def is[U: MonadicReader](f: U => Boolean): JsPred = ???

    def andThen(next: ScalarProjection): OptProjection  = new Joined(next) with OptProjectionImpl {
      def retr: JsValue => Validated[Option[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- swap(outerV.map(next.retr))
        } yield innerV
    }
    def andThen(next: OptProjection): OptProjection = new Joined(next) with OptProjectionImpl {
      def retr: JsValue => Validated[Option[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- swap(outerV.flatMap(x => swap(next.retr(x))))
        } yield innerV
    }

    def swap[T](v: Option[Validated[T]]): Validated[Option[T]] =
      v match {
        case None => Right(None)
        case Some(Right(x)) => Right(Some(x))
        case Some(Left(e)) => Left(e)
      }

    def swap[T](v: Validated[Option[T]]): Option[Validated[T]] =
      v match {
        case Right(None) => None
        case Right(Some(v)) => Some(Right(v))
        case Left(e) => Some(Left(e))
      }
  }

  def field(name: String): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      for {
        res   <- f(getField(parent))
      }
       yield JsObject(fields = parent.asJsObject.fields + (name -> res))

    def retr: JsValue => SafeJsValue = v =>
      getField(v)

    def getField(v: JsValue): SafeJsValue = asObj(v) flatMap { o =>
      o.fields.get(name).getOrError("Expected field '%s' in '%s'" format (name, v))
    }
    def asObj(v: JsValue): Validated[JsObject] = v match {
      case o: JsObject =>
        Right(o)
      case e@_ =>
        unexpected("Not a json object: "+e)
    }
  }

  def element(idx: Int): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        if (idx < elements.size) {
          val (headEls, element::tail) = elements.splitAt(idx)
          f(Right(element)) map (v => JsArray(headEls ::: v :: tail))
        } else
          unexpected("Too little elements in array: %s size: %d index: %d" format (parent, elements.size, idx))
      case e@_ =>
        unexpected("Not a json array: "+e)
    }

    def retr: JsValue => SafeJsValue = {
      case a@JsArray(elements) =>
        if (idx < elements.size)
          Right(elements(idx))
        else
          outOfBounds("Too little elements in array: %s size: %d index: %d" format (a, elements.size, idx))
      case e@_ => unexpected("Not a json array: "+e)
    }
  }

  /**
   * The identity projection which operates on the current element itself
   */
  val value: ScalarProjection = new ScalarProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      f(Right(parent))

    def retr: JsValue => SafeJsValue = x => Right(x)
  }

  def elements: SeqProjection = ???

  def find(pred: JsPred): OptProjection = new OptProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        elements.span(x => !pred(x)) match {
          case (prefix, element :: suffix) =>
            f(Right(element)) map (v => JsArray(prefix ::: v :: suffix))

          // element not found, do nothing
          case _ =>
            Right(parent)
        }
      case e@_ => unexpected("Not a json array: "+e)
    }

    def retr: JsValue => Validated[Option[JsValue]] = {
      case JsArray(elements) => Right(elements.find(pred))
      case e@_ => unexpected("Not a json array: "+e)
    }
  }

  def set[T: JsonWriter](t: T): Operation = new Operation {
    def apply(value: SafeJsValue): SafeJsValue =
      Right(jsonWriter[T].write(t))
  }

  trait MapOperation extends Operation {
    def apply(value: JsValue): SafeJsValue

    def apply(value: SafeJsValue): SafeJsValue = value.flatMap(apply)
  }

  def updated[T: MonadicReader: JsonWriter](f: T => T): Operation = new MapOperation {
    def apply(value: JsValue): SafeJsValue =
      value.as[T] map (v => jsonWriter[T].write(f(v)))
  }

  def filter(pred: JsPred): SeqProjection = ???
  def append(update: Update): Operation = ???
  def update(update: Update): Operation = ???

  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = ???

  def ??? = sys.error("NYI")

  def unexpected(message: String) = Left(new RuntimeException(message))
  def outOfBounds(message: String) = Left(new IndexOutOfBoundsException(message))
}