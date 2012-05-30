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

  trait Update { outer =>
    def apply(value: JsValue): JsValue

    def &&(next: Update): Update = new Update {
      def apply(value: JsValue): JsValue = next(outer(value))
    }
  }

  implicit def strToField(name: String): ScalarProjection = field(name)

  trait Extractor[T] {
    def apply[M[_]](p: Projection[M]): M[T]
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

    def /[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R]

    def ops: Ops[M]
  }

  type ScalarProjection = Projection[Id]
  type OptProjection = Projection[Option]
  type SeqProjection = Projection[Seq]

  trait Join[M1[_], M2[_], R[_]] {
    def get(outer: Ops[M1], inner: Ops[M2]): Ops[R]
  }
  object Join {
    def apply[M1[_], M2[_], R[_]](f: ((Ops[M1], Ops[M2])) => Ops[R]): Join[M1, M2, R] = new Join[M1, M2, R] {
      def get(outer: Ops[M1], inner: Ops[M2]): Ops[R] = f(outer, inner)
    }

    implicit def joinWithSeq[M2[_]]: Join[Seq, M2, Seq] = Join(_._1)
    implicit def joinWithScalar[M2[_]]: Join[Id, M2, M2] = Join(_._2)
    implicit def joinWithOptionWithId: Join[Option, Id, Option] = Join(_._1)
    implicit def joinOptionWithOption: Join[Option, Option, Option] = Join(_._1)
    implicit def joinOptionWithSeq: Join[Option, Seq, Seq] = Join(_._2)
  }

  trait Ops[M[_]] {
    def flatMap[T, U](els: M[T])(f: T => Seq[U]): Seq[U]
    def allRight[T](v: Seq[Validated[T]]): Validated[M[T]]
    def toSeq[T](v: Validated[M[T]]): Seq[Validated[T]]

    def map[T, U](els: M[T])(f: T => U): Seq[U] =
      flatMap(els)(v => Seq(f(v)))
  }
  object Ops {
    implicit def idOps: Ops[Id] = new Ops[Id] {
      def flatMap[T, U](els: T)(f: T => Seq[U]): Seq[U] = f(els)
      def allRight[T](v: Seq[Validated[T]]): Validated[T] = v.head
      def toSeq[T](v: Validated[T]): Seq[Validated[T]] = Seq(v)
    }
    implicit def optionOps: Ops[Option] = new Ops[Option] {
      def flatMap[T, U](els: Option[T])(f: T => Seq[U]): Seq[U] =
        els.toSeq.flatMap(f)

      def allRight[T](v: Seq[Validated[T]]): Validated[Option[T]] =
        v match {
          case Nil => Right(None)
          case Seq(Right(x)) => Right(Some(x))
          case Seq(Left(e)) => Left(e)
        }

      def toSeq[T](v: Validated[Option[T]]): Seq[Validated[T]] = v match {
        case Right(Some(x)) => Seq(Right(x))
        case Right(None) => Nil
        case Left(e) => Seq(Left(e))
      }
    }
    implicit def seqOps: Ops[Seq] = new Ops[Seq] {
      def flatMap[T, U](els: Seq[T])(f: T => Seq[U]): Seq[U] =
        els.flatMap(f)

      def allRight[T](v: Seq[Validated[T]]): Validated[Seq[T]] = {
        def inner(l: List[Validated[T]]): Validated[List[T]] = l match {
          case head :: tail =>
            for {
              headM <- head
              tailM <- inner(tail)
            } yield headM :: tailM
          case Nil =>
            Right(Nil)
        }
        inner(v.toList)
      }

      def toSeq[T](x: Validated[Seq[T]]): Seq[Validated[T]] = x match {
        case Right(x) => x.map(Right(_))
        case Left(e) => List(Left(e))
      }
    }
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
      ops.allRight(ops.map(value)(f))

    def is[U: MonadicReader](f: U => Boolean): JsPred =
      value => getSecure[U] apply value exists (x => ops.map(x)(f).forall(identity))

    def /[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R] = new Joined(next) with ProjectionImpl[R] {
      val ops: Ops[R] = ev.get(next.ops, outer.ops)
      def retr: JsValue => Validated[R[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- ops.allRight(outer.ops.flatMap(outerV)(x => next.ops.toSeq(next.retr(x))))
        } yield innerV
    }
  }
  abstract class Proj[M[_]](implicit val ops: Ops[M]) extends ProjectionImpl[M]

  def field(name: String): ScalarProjection = new Proj[Id] {
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

  def element(idx: Int): ScalarProjection = new Proj[Id] {
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
  val value: ScalarProjection = new Proj[Id] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      f(Right(parent))

    def retr: JsValue => SafeJsValue = x => Right(x)
  }

  def elements: SeqProjection = new Proj[Seq] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        ops.allRight(elements.map(x => f(Right(x)))).map(JsArray(_: _*))
      case e@_ => unexpected("Not a json array: "+e)
    }

    def retr: JsValue => Validated[Seq[JsValue]] = {
      case JsArray(elements) => Right(elements)
      case e@_ => unexpected("Not a json array: "+e)
    }
  }
  def filter(pred: JsPred): SeqProjection = new Proj[Seq] {
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
  def find(pred: JsPred): OptProjection = new Proj[Option] {
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
      Right(t.toJson)
  }

  trait MapOperation extends Operation {
    def apply(value: JsValue): SafeJsValue

    def apply(value: SafeJsValue): SafeJsValue = value.flatMap(apply)
  }

  def updated[T: MonadicReader: JsonWriter](f: T => T): Operation = new MapOperation {
    def apply(value: JsValue): SafeJsValue =
      value.as[T] map (v => f(v).toJson)
  }

  def append(update: Update): Operation = ???
  def update(update: Update): Operation = ???

  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = ???

  def ??? = sys.error("NYI")

  def unexpected(message: String) = Left(new RuntimeException(message))
  def outOfBounds(message: String) = Left(new IndexOutOfBoundsException(message))
}