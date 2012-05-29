package cc.spray.json

object JsonLenses {
  type JsPred = JsValue => Boolean
  type Id[T] = T

  trait Update {
    def apply(value: JsValue): JsValue

    def &&(next: Update): Update = null
  }

  implicit def strToField(name: String): ScalarProjection = field(name)

  trait Extractor[T] {
    def apply[M[_]](p: Projection[M]): M[T]
  }

  case class Updatable(value: JsValue) {
    def update(updater: Update): JsValue = updater(value)
    def extract[T](f: JsValue => T): T = f(value)
    def extract[T: JsonReader]: Extractor[T] = new Extractor[T] {
      def apply[M[_]](p: Projection[M]): M[T] = p.get[T] apply (value)
    }
  }

  implicit def updatable(value: JsValue): Updatable = Updatable(value)

  trait Operation {
    def apply(value: JsValue): JsValue
  }

  trait Projection[M[_]] {
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue
    def retr: JsValue => M[JsValue]
    def mapValue[T](value: M[JsValue])(f: JsValue => T): M[T]

    def ![U](op: Operation): Update
    def get[T: JsonReader]: JsValue => M[T]

    //def is(f: M[T] => Boolean): JsPred
    def is[U: JsonReader](f: U => Boolean): JsPred

    //def /[M2[_], R[_]](next: Projection[M2])(implicit conv: Conv[M, M2, R]): Projection[R]
  }

  /*
  trait Conv[M[_], M2[_], R[_]] {
    //def flatMap[T](first: M[T], second: M2[T])(op: (T, T) => T): R[T]
  }
  object Conv {
    implicit def joinScalar[M2[_]]: Conv[Id, M2, M2] = new Conv[Id, M2, M2] {
      def flatMap[T](first: T, second: M2[T])(op: (T, T) => T): M2[T] = null.asInstanceOf[M2[T]]

    }
    implicit def joinSeq[M2[_]]: Conv[Seq, M2, Seq] = null
    implicit def joinOptId: Conv[Option, Id, Option] = null
    implicit def joinOptOpt: Conv[Option, Option, Option] = null
    implicit def joinOptSeq: Conv[Option, Seq, Seq] = null
  }*/
  trait ScalarProjection extends Projection[Id] {
    def /(next: ScalarProjection): ScalarProjection
    def /(next: OptProjection): OptProjection
  }
  trait OptProjection extends Projection[Option] {
    def /(next: ScalarProjection): OptProjection
    def /(next: OptProjection): OptProjection
  }

  type SeqProjection = Projection[Seq]

  trait ProjectionImpl[M[_]] extends Projection[M] {
    def get[T: JsonReader]: JsValue => M[T] =
      p => mapValue(retr(p))(_.convertTo[T])

    def ![U](op: Operation): Update = new Update {
      def apply(parent: JsValue): JsValue =
        updated(op(_))(parent)
    }
  }

  trait ScalarProjectionImpl extends ScalarProjection with ProjectionImpl[Id] { outer =>
    def mapValue[T](value: JsValue)(f: JsValue => T): T =
      f(value)

    def is[U: JsonReader](f: U => Boolean): JsPred =
      value => f(get[U] apply (value))

    def /(next: ScalarProjection): ScalarProjection =
      new ScalarProjectionImpl {
        def updated(f: JsValue => JsValue)(parent: JsValue): JsValue =
          outer.updated(next.updated(f))(parent)

        def retr: JsValue => JsValue = parent =>
          next.retr(outer.retr(parent))
      }

    def /(next: OptProjection): OptProjection = null
  }

  trait OptProjectionImpl extends OptProjection with ProjectionImpl[Option] {
    def mapValue[T](value: Option[JsValue])(f: JsValue => T): Option[T] = value.map(f)

    def is[U: JsonReader](f: U => Boolean): JsPred = null

    def /(next: ScalarProjection): OptProjection = null
    def /(next: OptProjection): OptProjection = null
  }

  def field(name: String): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: (JsValue) => JsValue)(parent: JsValue): JsValue =
      JsObject(fields = parent.asJsObject.fields + (name -> f(retr(parent))))

    def retr: JsValue => JsValue = _.asJsObject.fields(name)
  }

  def element(idx: Int): ScalarProjection = new ScalarProjectionImpl {
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue = {
      val theArray = parent.asInstanceOf[JsArray]
      val (headEls, element::tail) = theArray.elements.splitAt(idx)
      JsArray(headEls ::: f(element) :: tail)
    }

    def retr: JsValue => JsValue =
      _.asInstanceOf[JsArray].elements(idx)
  }

  /**
   * The identity projection which operates on the current element itself
   */
  val value: ScalarProjection = new ScalarProjectionImpl {
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue =
      f(parent)

    def retr: JsValue => JsValue = identity
  }

  def elements: SeqProjection = null

  def find(pred: JsPred): OptProjection = new OptProjectionImpl {
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue = {
      parent.asInstanceOf[JsArray].elements.span(x => !pred(x)) match {
        case (prefix, element :: suffix) =>
          JsArray(prefix ::: f(element) :: suffix)

        // element not found, do nothing
        case _ => parent
      }
    }

    def retr: JsValue => Option[JsValue] =
      _.asInstanceOf[JsArray].elements.find(pred)
  }

  def filter(pred: JsPred): SeqProjection = null

  def set[T: JsonWriter](t: T): Operation = new Operation {
    def apply(value: JsValue): JsValue =
      jsonWriter[T].write(t)
  }

  def updated[T: JsonFormat](f: T => T): Operation = new Operation {
    def apply(value: JsValue): JsValue =
      jsonWriter[T].write(f(jsonReader[T].read(value)))
  }

  def append(update: Update): Operation = null

  def update(update: Update): Operation = null

  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = null
}