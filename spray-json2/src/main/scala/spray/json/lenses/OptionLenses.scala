package spray.json
package lenses

trait OptionLenses {
  /**
   * Operates on the first element of an JsArray which matches the predicate.
   */
  def find(pred: JsPred): OptLens = new LensImpl[Option] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        elements.span(x => !pred(x)) match {
          case (prefix, element :: suffix) =>
            f(Success(element)) map (v => JsArray(prefix ++: v +: suffix))

          // element not found, do nothing
          case _ =>
            Success(parent)
        }
      case e@_ => unexpected("Not a json array: " + e)
    }

    def retr: JsValue => Validated[Option[JsValue]] = {
      case JsArray(elements) => Success(elements.find(pred))
      case e@_ => unexpected("Not a json array: " + e)
    }
  }
}

object OptionLenses extends OptionLenses