package spray.json
package lenses

/**
 * A Lens combines read and update functions of UpdateLens and ReadLens into
 * combinable chunks.
 *
 * A lens can either operate on a scalar value, or on an optional value, or on a
 * sequence value. This is denoted by the `M[_]` type constructor.
 */
trait Lens[M[_]] extends UpdateLens with ReadLens[M] {
  /**
   * A shortcut for the `combine` lens which combines two lenses.
   */
  def /[M2[_], R[_]](next: Lens[M2])(implicit ev: Join[M2, M, R]): Lens[R]

  def toSeq: Lens[Seq]

  def ops: Ops[M]
}

/**
 * This implements most of the methods of `Lens`. Implementors of a new type of lens
 * must implement `retr` for the read side of the lens and `updated` for the update side of the lens.
 */
abstract class LensImpl[M[_]](implicit val ops: Ops[M]) extends Lens[M] { outer =>
  import ExtraImplicits.richValue

  def tryGet[T: Reader](p: JsValue): Validated[M[T]] =
    retr(p).flatMap(mapValue(_)(_.toValidated[T]))

  def get[T: Reader](p: JsValue): M[T] =
    tryGet[T](p).get

  def !(op: Operation): Update = new Update {
    def apply(parent: JsValue): JsValue =
      updated(op)(parent).get
  }

  def is[U: Reader](f: U => Boolean): JsPred = value =>
    tryGet[U](value) exists (x => ops.map(x)(f).forall(identity))

  def /[M2[_], R[_]](next: Lens[M2])(implicit ev: Join[M2, M, R]): Lens[R] =
    JsonLenses.combine(this, next)

  def toSeq: Lens[Seq] = this / SeqLenses.asSeq

  private[this] def mapValue[T](value: M[JsValue])(f: JsValue => Validated[T]): Validated[M[T]] =
    ops.allRight(ops.map(value)(f))
}
