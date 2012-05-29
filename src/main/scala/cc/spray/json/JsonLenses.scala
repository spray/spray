package cc.spray.json

object JsonLenses {
  type JsPred = JsValue => Boolean

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

    def ![U](update: Operation): Update
    def get[T: JsonReader]: JsValue => M[T]

    //def is(f: M[T] => Boolean): JsPred
    def is[U](f: U => Boolean): JsPred

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
  }
  type OptProjection = Projection[Option]
  type SeqProjection = Projection[Seq]

  type Id[T] = T

  trait SimpleProjection extends ScalarProjection {
    outer =>
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue

    def retr: JsValue => JsValue

    def get[T: JsonReader]: JsValue => T =
      p => retr(p).convertTo[T]

    def ![U](op: Operation): Update = new Update {
      def apply(parent: JsValue): JsValue = updated(op(_))(parent)
    }

    def is[U](f: U => Boolean): JsPred = null

    def /(next: ScalarProjection): ScalarProjection =
      new SimpleProjection {
        def updated(f: JsValue => JsValue)(parent: JsValue): JsValue =
          outer.updated(next.updated(f))(parent)

        def retr: JsValue => JsValue = parent =>
          next.retr(outer.retr(parent))
      }
  }

  def field(name: String): ScalarProjection = new SimpleProjection {
    def updated(f: (JsValue) => JsValue)(parent: JsValue): JsValue =
      JsObject(fields = parent.asJsObject.fields + (name -> f(retr(parent))))

    def retr: JsValue => JsValue = _.asJsObject.fields(name)
  }

  def element(idx: Int): ScalarProjection = new SimpleProjection {
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue = {
      val theArray = parent.asInstanceOf[JsArray]
      val (headEls, element::tail) = theArray.elements.splitAt(idx)
      JsArray(headEls ::: f(element) :: tail)
    }

    def retr: JsValue => JsValue =
      _.asInstanceOf[JsArray].elements(idx)
  }

  def elements: SeqProjection = null

  def find(pred: JsPred): OptProjection = null

  def filter(pred: JsPred): SeqProjection = null

  def set[T: JsonWriter](t: T): Operation = new Operation {
    def apply(value: JsValue): JsValue = jsonWriter[T].write(t)
  }

  def updated[T: JsonFormat](f: T => T): Operation = new Operation {
    def apply(value: JsValue): JsValue = jsonWriter[T].write(f(jsonReader[T].read(value)))
  }

  def append(update: Update): Operation = null

  def update(update: Update): Operation = null

  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = null
}