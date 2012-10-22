package spray.json

case class DynamicJsValue(value: JsValue) extends Dynamic {
  def applyDynamic(key: String)(index: Int = -1): DynamicValidatedJsValue =
    (if (index == -1) value(key) else value(key).flatMap(_(index))).dyn
}

object DynamicJsValue {
  implicit def autoUnpack(dyn: DynamicJsValue): JsValue = dyn.value
}
