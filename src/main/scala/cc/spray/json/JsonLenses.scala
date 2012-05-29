package cc.spray.json

object Lens2 {
  type JsPred = JsValue => Boolean

  trait Update {
    def apply(value: JsValue): JsValue

    def &&(next: Update): Update = null
  }

  implicit def strToField(name: String): ScalarProjection = field(name)

  case class Updatable(value: JsValue) {
    def update(updater: Update): JsValue = updater(value)
  }

  implicit def updatable(value: JsValue): Updatable = Updatable(value)

  trait Operation {
    def apply(value: JsValue): JsValue
  }

  trait Projection[M[_]] {

    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue

    //def !(update: Operation[M[T]]): Update
    def ![U](update: Operation): Update

    def get[T: JsonReader]: JsValue => M[T]

    def retr: JsValue => M[JsValue]

    def set[T: JsonWriter](t: T): Update

    //def is(f: M[T] => Boolean): JsPred
    def is[U](f: U => Boolean): JsPred

    //def as[U](implicit ev: T <:< JsValue, reader: JsonReader[U]): Projection[M, U]

    //def /[M2[_], R[_]](next: Projection[M2])(implicit conv: Conv[M, M2, R]): Projection[R]
    //def /[M2[_]](next: Projection[M2]): Projection


  }

  /*
  trait Map[M[_]] {
    def map[T, U](m: M[T]): M[U]
  }

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

  //type ScalarProjection = Projection[Id]
  trait ScalarProjection extends Projection[Id] {
    def /(next: ScalarProjection): ScalarProjection
  }

  type OptProjection = Projection[Option]
  type SeqProjection = Projection[Seq]

  type Id[T] = T

  def field(name: String): ScalarProjection = new SimpleProjection {

    def updated(f: (JsValue) => JsValue)(parent: JsValue): JsValue =
      JsObject(fields = parent.asJsObject.fields + (name -> f(retr(parent))))

    def retr: JsValue => JsValue = _.asJsObject.fields(name)
  }

  trait SimpleProjection extends ScalarProjection {
    outer =>
    def updated(f: JsValue => JsValue)(parent: JsValue): JsValue

    def retr: JsValue => JsValue

    def get[T: JsonReader]: JsValue => T =
      p => retr(p).convertTo[T]

    def ![U](op: Operation): Update = new Update {
      def apply(parent: JsValue): JsValue = updated(op(_))(parent)
    }

    def set[T: JsonWriter](t: T): Update = this ! Lens2.set(t)

    def is[U](f: U => Boolean): JsPred = null


    def /(next: ScalarProjection): ScalarProjection =
      new SimpleProjection {
        def updated(f: JsValue => JsValue)(parent: JsValue): JsValue =
          outer.updated(next.updated(f))(parent)

        def retr: JsValue => JsValue = parent => next.retr(outer.retr(parent))
      }
  }

  def element(idx: Int): ScalarProjection = null

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