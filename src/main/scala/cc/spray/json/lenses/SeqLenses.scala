package cc.spray.json
package lenses

trait SeqLenses {
  /**
   * The projection which just converts another Projection into one of a
   * Seq value.
   */
  val asSeq: SeqProjection = new Proj[Seq] {
    def updated(f: Operation)(parent: JsValue): SafeJsValue =
      f(Right(parent))

    def retr: JsValue => Validated[Seq[JsValue]] = x => Right(Seq(x))
  }

  /**
   * All the elements of a JsArray.
   */
  val elements: SeqProjection = new Proj[Seq] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        ops.allRight(elements.map(x => f(Right(x)))).map(JsArray(_: _*))
      case e@_ => unexpected("Not a json array: " + e)
    }

    def retr: JsValue => Validated[Seq[JsValue]] = {
      case JsArray(elements) => Right(elements)
      case e@_ => unexpected("Not a json array: " + e)
    }
  }

  /**Alias for `elements`*/
  def * = elements

  /**
   * All the values of a JsArray which match the predicate.
   */
  def filter(pred: JsPred): SeqProjection = new Proj[Seq] {
    def updated(f: SafeJsValue => SafeJsValue)(parent: JsValue): SafeJsValue = parent match {
      case JsArray(elements) =>
        ops.allRight(elements.map(x => if (pred(x)) f(Right(x)) else Right(x))).map(JsArray(_: _*))

      case e@_ => unexpected("Not a json array: " + e)
    }

    def retr: JsValue => Validated[Seq[JsValue]] = {
      case JsArray(elements) =>
        Right(elements.filter(pred))
      case e@_ => unexpected("Not a json array: " + e)
    }
  }
}

object SeqLenses extends SeqLenses