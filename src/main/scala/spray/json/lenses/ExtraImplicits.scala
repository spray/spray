package spray.json
package lenses

trait ExtraImplicits {
  trait RichJsValue {
    def value: JsValue

    def update(updater: Update): JsValue = updater(value)

    def update[T: JsonWriter, M[_]](lens: UpdateLens, pValue: T): JsValue =
      lens ! Operations.set(pValue) apply value

    // This can't be simplified because we don't want the type constructor
    // for Lens[M] to appear in the type paramater list.
    def extract[T: Reader](p: Lens[Id]): T =
      p.get[T](value)

    def extract[T: Reader](p: Lens[Option]): Option[T] =
      p.get[T](value)

    def extract[T: Reader](p: Lens[Seq]): Seq[T] =
      p.get[T](value)
  }

  implicit def richValue(v: JsValue): RichJsValue = new RichJsValue { def value = v }
  implicit def richString(str: String): RichJsValue = new RichJsValue { def value = JsonParser(str) }
}

object ExtraImplicits extends ExtraImplicits