package cc.spray.json

import annotation.unchecked.uncheckedVariance

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
    def apply(value: Option[JsValue]): SafeJsValue
  }

  trait Projection[M[_]] {
    type ThenScalar

    def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue
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

  trait ProjectionImpl[M[_]] extends Projection[M] {
    def getSecure[T: MonadicReader]: JsValue => Validated[M[T]] =
      p => retr(p).flatMap(mapValue(_)(_.as[T]))

    def get[T: MonadicReader]: JsValue => M[T] =
      p => getSecure[T].apply(p).getOrThrow

    def ![U](op: Operation): Update = new Update {
      def apply(parent: JsValue): JsValue =
        updated(op(_))(parent).getOrThrow
    }
  }

  trait ScalarProjectionImpl extends ScalarProjection with ProjectionImpl[Id] { outer =>
    def mapValue[T](value: JsValue)(f: JsValue => Validated[T]): Validated[T] =
      f(value)

    def is[U: MonadicReader](f: U => Boolean): JsPred =
      value => getSecure[U] apply value exists f

    def andThen(next: ScalarProjection): ScalarProjection =
      new ScalarProjectionImpl {
        def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue =
          outer.updated(v => next.updated(f)(v.get))(parent)

        def retr: JsValue => SafeJsValue = parent =>
          for {
            outerV <- outer.retr(parent)
            innerV <- next.retr(outerV)
          } yield innerV

      }

    def andThen(next: OptProjection): OptProjection = ???
  }

  trait OptProjectionImpl extends OptProjection with ProjectionImpl[Option] {
    def mapValue[T](value: Option[JsValue])(f: JsValue => Validated[T]): Validated[Option[T]] =
      value.map(f) match {
        case None => Right(None)
        case Some(Right(x)) => Right(Some(x))
        case Some(Left(e)) => Left(e)
      }

    def is[U: MonadicReader](f: U => Boolean): JsPred = ???

    def andThen(next: ScalarProjection): OptProjection = ???
    def andThen(next: OptProjection): OptProjection = ???
  }

  def field(name: String): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue =
      for {
        child <- getField(parent)
        res   <- f(child)
      }
       yield JsObject(fields = parent.asJsObject.fields + (name -> res))

    def retr: JsValue => SafeJsValue = v =>
      getField(v).flatMap {
        _.map(Right(_)).getOrElse(Left(new IllegalArgumentException("Expected field '%s' in '%s'" format (name, v))))
      }

    def getField(v: JsValue): Validated[Option[JsValue]] = asObj(v) map { o =>
      o.fields.get(name)
    }
    def asObj(v: JsValue): Validated[JsObject] = v match {
      case o: JsObject =>
        Right(o)
      case e@_ =>
        Left(new IllegalArgumentException("Not a json object: "+e))
    }
  }

  def element(idx: Int): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        if (idx < elements.size) {
          val (headEls, element::tail) = elements.splitAt(idx)
          f(Some(element)) map (v => JsArray(headEls ::: v :: tail))
        } else
          Left(new IndexOutOfBoundsException("Too little elements in array: %s size: %d index: %d" format (parent, elements.size, idx)))
      case e@_ =>
        Left(new IllegalArgumentException("Not a json array: "+e))
    }

    def retr: JsValue => SafeJsValue = {
      case a@JsArray(elements) =>
        if (idx < elements.size)
          Right(elements(idx))
        else
          Left(new IndexOutOfBoundsException("Too little elements in array: %s size: %d index: %d" format (a, elements.size, idx)))
      case e@_ => Left(new IllegalArgumentException("Not a json array: "+e))
    }
  }

  /**
   * The identity projection which operates on the current element itself
   */
  val value: ScalarProjection = new ScalarProjectionImpl {
    def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue =
      f(Some(parent))

    def retr: JsValue => SafeJsValue = x => Right(x)
  }

  def elements: SeqProjection = ???

  def find(pred: JsPred): OptProjection = new OptProjectionImpl {
    def updated(f: Option[JsValue] => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        elements.span(x => !pred(x)) match {
          case (prefix, element :: suffix) =>
            f(Some(element)) map (v => JsArray(prefix ::: v :: suffix))

          // element not found, do nothing
          case _ =>
            Right(parent)
        }
      case e@_ => Left(new IllegalArgumentException("Not a json array: "+e))
    }

    def retr: JsValue => Validated[Option[JsValue]] = {
      case JsArray(elements) => Right(elements.find(pred))
      case e@_ => Left(new IllegalArgumentException("Not a json array: "+e))
    }
  }

  def set[T: JsonWriter](t: T): Operation = new Operation {
    def apply(value: Option[JsValue]): SafeJsValue =
      Right(jsonWriter[T].write(t))
  }

  trait MapOperation extends Operation {
    def apply(value: JsValue): SafeJsValue

    def apply(value: Option[JsValue]): SafeJsValue = value match {
      case Some(x) => apply(x)
      case None => Left(new IllegalArgumentException("Need a value to operate on"))
    }
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
}