package cc.spray.json
package lenses

trait ScalarLenses {
  def field(name: String): ScalarProjection = new Proj[Id] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      for (updatedValue <- f(retr(parent)))
        // asJsObject is already guarded by getField above, FIXME: is it really?
        yield JsObject(fields = parent.asJsObject.fields + (name -> updatedValue))

    def retr: JsValue => SafeJsValue = v => asObj(v) flatMap {
      _.fields.get(name).getOrError("Expected field '%s' in '%s'" format(name, v))
    }

    def asObj(v: JsValue): Validated[JsObject] = v match {
      case o: JsObject => Right(o)
      case e@_         => unexpected("Not a json object: " + e)
    }
  }

  def element(idx: Int): ScalarProjection = new Proj[Id] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        if (idx < elements.size) {
          val (headEls, element :: tail) = elements.splitAt(idx)
          f(Right(element)) map (v => JsArray(headEls ::: v :: tail))
        } else
          unexpected("Too little elements in array: %s size: %d index: %d" format(parent, elements.size, idx))
      case e@_ =>
        unexpected("Not a json array: " + e)
    }

    def retr: JsValue => SafeJsValue = {
      case a@JsArray(elements) =>
        if (idx < elements.size)
          Right(elements(idx))
        else
          outOfBounds("Too little elements in array: %s size: %d index: %d" format(a, elements.size, idx))
      case e@_ => unexpected("Not a json array: " + e)
    }
  }

  /**
   * The identity projection which operates on the current element itself
   */
  val value: ScalarProjection = new Proj[Id] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue =
      f(Right(parent))

    def retr: JsValue => SafeJsValue = x => Right(x)
  }
}

object ScalarLenses extends ScalarLenses