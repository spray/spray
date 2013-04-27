package spray.json
package lenses

trait Update extends (JsValue ⇒ JsValue) { outer ⇒
  def apply(value: JsValue): JsValue

  def apply(jsonString: String): String =
    apply(JsonParser(jsonString)).toString()

  def &&(next: Update): Update = new Update {
    def apply(value: JsValue): JsValue = next(outer(value))
  }
}

/**
 * The UpdateLens is the central interface for updating a child element somewhere
 * deep down a hierarchy of a JsValue.
 */
trait UpdateLens {
  /**
   * Applies function `f` on the child of the `parent` denoted by this UpdateLens
   * and returns a `Right` of the parent with the child element updated.
   *
   * The value passed to `f` may be `Left(e)` if the child could not be found
   * in which case particular operations may still succeed. Function `f` may return
   * `Left(error)` in case the operation fails.
   *
   * `updated` returns `Left(error)` if the update operation or any of any intermediate
   * lens fails.
   */
  def updated(f: Operation)(parent: JsValue): SafeJsValue

  def !(op: Operation): Update
}