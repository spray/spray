package spray.json
package lenses

/**
 * An aggregate option to import all of the functionality of JsonLenses with one
 * import.
 */
object JsonLenses extends
  ScalarLenses with
  OptionLenses with
  SeqLenses with
  Operations with
  JsonPathIntegration with
  ExtraImplicits {

  implicit def strToField(name: String): ScalarLens = field(name)
  implicit def symbolToField(sym: Symbol): ScalarLens = field(sym.name)

  /**
   * The lens which combines an outer lens with an inner.
   */
  def combine[M[_], M2[_], R[_]](outer: Lens[M], inner: Lens[M2])(implicit ev: Join[M2, M, R]): Lens[R] =
    new LensImpl[R]()(ev.get(inner.ops, outer.ops)) {
      def retr: JsValue => Validated[R[JsValue]] = parent =>
        for {
          outerV <- outer.retr(parent)
          innerV <- ops.allRight(outer.ops.flatMap(outerV)(x => inner.ops.toSeq(inner.retr(x))))
        } yield innerV

      def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
        outer.updated(_.flatMap(inner.updated(f)))(parent)
    }
}