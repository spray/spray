package cc.spray.json
package lenses

/**
 * Defines a set of operations to update Json values.
 */
trait Operations {
  /**
   * The set operation sets or creates a value.
   */
  def set[T: JsonWriter](t: T): Operation = new Operation {
    def apply(value: SafeJsValue): SafeJsValue =
    // ignore existence of old value
      Right(t.toJson)
  }

  /**
   * A MapOperation is one that expect an old value to be available.
   */
  trait MapOperation extends Operation {
    def apply(value: JsValue): SafeJsValue

    def apply(value: SafeJsValue): SafeJsValue = value.flatMap(apply)
  }

  /**
   * The `updated` operation applies a function on the (converted) value
   */
  def updated[T: Reader : JsonWriter](f: T => T): Operation = new MapOperation {
    def apply(value: JsValue): SafeJsValue =
      value.as[T] map (v => f(v).toJson)
  }

  def append(update: Update): Operation = ???
  def update(update: Update): Operation = ???
  def extract[M[_], T](value: Projection[M])(f: M[T] => Update): Operation = ???
}

object Operations extends Operations