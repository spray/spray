package cc.spray.json
package lenses

trait SeqLenses {
  val asSeq: SeqProjection = new Proj[Seq] {
    def updated(f: Operation)(parent: JsValue): SafeJsValue =
      f(Right(parent))

    def retr: JsValue => Validated[Seq[JsValue]] = x => Right(Seq(x))
  }

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