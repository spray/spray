package cc.spray.json
package lenses

/**
 * The read lens can extract child values out of a JsValue hierarchy. A read lens
 * is parameterized with a type constructor. This allows to extracts not only scalar
 * values but also sequences or optional values.
 * @tparam M
 */
trait ReadLens[M[_]] {
  /**
   * Given a parent JsValue, tries to extract the child value.
   * @return `Right(value)` if the projection succeeds. `Left(error)` if the projection
   *         fails.
   */
  def retr: JsValue => Validated[M[JsValue]]

  /**
   * Given a parent JsValue extracts and tries to convert the JsValue into
   * a value of type `T`
   */
  def tryGet[T: Reader](value: JsValue): Validated[M[T]]

  /**
   * Given a parent JsValue extracts and converts a JsValue into a value of
   * type `T` or throws an exception.
   */
  def get[T: Reader](value: JsValue): M[T]

  /**
   * Lifts a predicate for a converted value for this lens up to the
   * parent level.
   */
  def is[U: Reader](f: U => Boolean): JsPred
}