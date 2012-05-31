package cc.spray.json
package lenses

/**
 * A projection combines read and update functions of UpdateLens and ReadLens into
 * combinable chunks.
 * @tparam M
 */
trait Projection[M[_]] extends UpdateLens with ReadLens[M] {
  def /[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R]

  def toSeq: Projection[Seq]

  def ops: Ops[M]
}

/**
 * This implements most of the methods of `Projection`. Implementors of a new type of projection
 * must implement `retr` for the read side of the lens and `updated` for the update side of the lens.
 */
trait ProjectionImpl[M[_]] extends Projection[M] {
  outer =>
  def tryGet[T: MonadicReader](p: JsValue): Validated[M[T]] =
    retr(p).flatMap(mapValue(_)(_.as[T]))

  def get[T: MonadicReader](p: JsValue): M[T] =
    tryGet[T](p).getOrThrow

  def !(op: Operation): Update = new Update {
    def apply(parent: JsValue): JsValue =
      updated(op)(parent).getOrThrow
  }

  def is[U: MonadicReader](f: U => Boolean): JsPred = value =>
    tryGet[U](value) exists (x => ops.map(x)(f).forall(identity))

  def /[M2[_], R[_]](next: Projection[M2])(implicit ev: Join[M2, M, R]): Projection[R] = new ProjectionImpl[R] {
    val ops: Ops[R] = ev.get(next.ops, outer.ops)

    def retr: JsValue => Validated[R[JsValue]] = parent =>
      for {
        outerV <- outer.retr(parent)
        innerV <- ops.allRight(outer.ops.flatMap(outerV)(x => next.ops.toSeq(next.retr(x))))
      } yield innerV

    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      outer.updated(_.flatMap(next.updated(f)))(parent)
  }

  def toSeq: Projection[Seq] = this / SeqLenses.asSeq

  private[this] def mapValue[T](value: M[JsValue])(f: JsValue => Validated[T]): Validated[M[T]] =
    ops.allRight(ops.map(value)(f))
}

abstract class Proj[M[_]](implicit val ops: Ops[M]) extends ProjectionImpl[M]