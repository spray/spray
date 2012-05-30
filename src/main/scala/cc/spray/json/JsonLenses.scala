package cc.spray.json

import annotation.tailrec

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
    def retr: JsValue => Validated[M[JsValue]]
    def mapValue[T](value: M[JsValue])(f: JsValue => Validated[T]): Validated[M[T]]

    def getSecure[T: MonadicReader]: JsValue => Validated[M[T]]
    def get[T: MonadicReader]: JsValue => M[T]
    def ![U](op: Operation): Update

    def is[U: MonadicReader](f: U => Boolean): JsPred

    def andThen[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R]

    def /(fieldName: String) = this andThen fieldName
    def apply(idx: Int) = element(idx)
    def element(idx: Int) = this andThen JsonLenses.element(idx)

    def mon: Monad[M]
  }

  type ScalarProjection = Projection[Id]
  type OptProjection = Projection[Option]
  type SeqProjection = Projection[Seq]

  trait Join[M1[_], M2[_], R[_]] {
    def get(outer: Monad[M1], inner: Monad[M2]): Monad[R]
  }
  object Join {
    implicit def joinWithSeq[M2[_]]: Join[Seq, M2, Seq] = new Join[Seq, M2, Seq] {
      def get(outer: Monad[Seq], inner: Monad[M2]): Monad[Seq] = outer
    }
    implicit def joinWithScalar[M2[_]]: Join[Id, M2, M2] = new Join[Id, M2, M2] {
      def get(outer: Monad[Id], inner: Monad[M2]): Monad[M2] = inner
    }

    implicit def joinWithOptionWithId = new Join[Option, Id, Option] {
      def get(outer: Monad[Option], inner: Monad[Id]): Monad[Option] = outer
    }
    implicit def joinOptionWithOption = new Join[Option, Option, Option] {
      def get(outer: Monad[Option], inner: Monad[Option]): Monad[Option] = outer
    }
    implicit def joinOptionWithSeq = new Join[Option, Seq, Seq] {
      def get(outer: Monad[Option], inner: Monad[Seq]): Monad[Seq] = inner
    }
  }

  trait Monad[M[_]] {
    def flatMap[T, U](els: M[JsValue])(f: JsValue => Seq[U]): Seq[U]
    def allRight[T](v: Seq[Validated[T]]): Validated[M[T]]
    def swap[T](v: Validated[M[T]]): Seq[Validated[T]]

    def map[T, U](els: M[JsValue])(f: JsValue => U): Seq[U] =
      flatMap(els)(v => Seq(f(v)))
  }

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

    def mapValue[T](value: M[JsValue])(f: JsValue => Validated[T]): Validated[M[T]] =
      mon.allRight(mon.map(value)(f))

    def is[U: MonadicReader](f: (U) => Boolean): JsonLenses.JsPred = ???

    def andThen[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R] = new Joined(next) with ProjectionImpl[R] {
      def mon: Monad[R] = ev.get(next.mon, outer.mon)
      def retr: JsValue => Validated[R[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- mon.allRight(outer.mon.flatMap(outerV)(x => next.mon.swap(next.retr(x))))
        } yield innerV
    }
  }

  trait ScalarProjectionImpl extends ScalarProjection with ProjectionImpl[Id] { outer =>
    override def is[U: MonadicReader](f: U => Boolean): JsPred =
      value => getSecure[U] apply value exists f

    def mon: Monad[Id] = new Monad[Id] {
      def flatMap[T, U](els: JsValue)(f: JsValue => Seq[U]): Seq[U] = f(els)
      def allRight[T](v: Seq[Validated[T]]): Validated[T] = v.head
      def swap[T](v: Validated[T]): Seq[Validated[T]] = Seq(v)
    }
  }

  trait OptProjectionImpl extends OptProjection with ProjectionImpl[Option] { outer =>
    def mon: Monad[Option] = new Monad[Option] {
      def flatMap[T, U](els: Option[JsValue])(f: JsValue => Seq[U]): Seq[U] =
        els.toSeq.flatMap(f)

      def allRight[T](v: Seq[Validated[T]]): Validated[Option[T]] =
        v match {
          case Nil => Right(None)
          case Seq(Right(x)) => Right(Some(x))
          case Seq(Left(e)) => Left(e)
        }

      def swap[T](v: Validated[Option[T]]): Seq[Validated[T]] = v match {
        case Right(Some(x)) => Seq(Right(x))
        case Right(None) => Nil
        case Left(e) => Seq(Left(e))
      }
    }
  }

  trait SeqProjectionImpl extends SeqProjection with ProjectionImpl[Seq] { outer =>
    def mon: Monad[Seq] = new Monad[Seq] {
      def flatMap[T, U](els: Seq[JsValue])(f: JsValue => Seq[U]): Seq[U] =
        els.flatMap(f)

      def allRight[T](v: Seq[Validated[T]]): Validated[Seq[T]] =
        allRightF(v)

      def swap[T](x: Validated[Seq[T]]): Seq[Validated[T]] = x match {
        case Right(x) => x.map(Right(_))
        case Left(e) => List(Left(e))
      }
    }
  }

  def field(name: String): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      for {
        res <- f(getField(parent))
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

  def elements: SeqProjection = new SeqProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        mapAllRight(elements)(v => f(Right(v))) map (JsArray(_: _*))
      case e@_ => unexpected("Not a json array: "+e)
    }

    def retr: JsValue => Validated[Seq[JsValue]] = {
      case JsArray(elements) => Right(elements)
      case e@_ => unexpected("Not a json array: "+e)
    }
  }
  def filter(pred: JsPred): SeqProjection = new SeqProjectionImpl {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      //case JsArray(elements) =>


      case e@_ => unexpected("Not a json array: "+e)
    }

    def retr: JsValue => Validated[Seq[JsValue]] = {
      case JsArray(elements) =>
        Right(elements.filter(pred))
      case e@_ => unexpected("Not a json array: "+e)
    }
  }
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

  def append(update: Update): Operation = ???
  def update(update: Update): Operation = ???

  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = ???

  def ??? = sys.error("NYI")

  def unexpected(message: String) = Left(new RuntimeException(message))
  def outOfBounds(message: String) = Left(new IndexOutOfBoundsException(message))

  def mapAllRight[T, U](l: List[T])(f: T => Validated[U]): Validated[Seq[U]] = {
    def inner(l: List[T]): Validated[List[U]] = l match {
      case head :: tail =>
        for {
          headM <- f(head)
          tailM <- inner(tail)
        } yield headM :: tailM
      case Nil =>
        Right(Nil)
    }
    inner(l)
  }
  def allRightF[T](l: Seq[Validated[T]]): Validated[Seq[T]] =
    mapAllRight(l.toList)(identity)
}