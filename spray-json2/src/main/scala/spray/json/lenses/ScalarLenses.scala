package spray.json
package lenses

trait ScalarLenses {
  /**
   * Accesses a field of a JsObject.
   */
  def field(name: String): ScalarLens = new LensImpl[Id] {
    def updated(f: SafeJsValue ⇒ SafeJsValue)(parent: JsValue): SafeJsValue =
      for (updatedValue ← f(retr(parent)))
        // asJsObject is already guarded by getField above, FIXME: is it really?
        yield JsObject(fields = parent.asInstanceOf[JsObject].fields + (name -> updatedValue))

    def retr: JsValue ⇒ SafeJsValue = v ⇒ asObj(v) flatMap {
      _.fields.get(name).getOrError("Expected field '%s' in '%s'" format (name, v))
    }

    def asObj(v: JsValue): Validated[JsObject] = v match {
      case o: JsObject ⇒ Success(o)
      case e @ _       ⇒ unexpected("Not a json object: " + e)
    }
  }

  /**
   * Accesses an element of a JsArray.
   */
  def element(idx: Int): ScalarLens = new LensImpl[Id] {
    def updated(f: SafeJsValue ⇒ SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) ⇒
        if (idx < elements.size) {
          val (headEls, element :: tail) = elements.splitAt(idx)
          f(Success(element)) map (v ⇒ JsArray(headEls ++: v +: tail))
        } else
          unexpected("Too little elements in array: %s size: %d index: %d" format (parent, elements.size, idx))
      case e @ _ ⇒
        unexpected("Not a json array: " + e)
    }

    def retr: JsValue ⇒ SafeJsValue = {
      case a @ JsArray(elements) ⇒
        if (idx < elements.size)
          Success(elements(idx))
        else
          outOfBounds("Too little elements in array: %s size: %d index: %d" format (a, elements.size, idx))
      case e @ _ ⇒ unexpected("Not a json array: " + e)
    }
  }

  /**
   * The identity lens which operates on the current element itself
   */
  val value: ScalarLens = new LensImpl[Id] {
    def updated(f: SafeJsValue ⇒ SafeJsValue)(parent: JsValue): SafeJsValue =
      f(Success(parent))

    def retr: JsValue ⇒ SafeJsValue = x ⇒ Success(x)
  }
}

object ScalarLenses extends ScalarLenses