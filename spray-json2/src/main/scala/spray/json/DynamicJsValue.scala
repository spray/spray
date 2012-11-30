package spray.json

case class DynamicJsValue(_value: JsValue) extends Dynamic {
  def applyDynamic(key: String)(index: Int = -1): DynamicValidatedJsValue =
    (if (index == -1) _value(key) else _value(key).flatMap(_(index))).dyn

  def selectDynamic(key: String): DynamicValidatedJsValue =
    _value(key).dyn
}

object DynamicJsValue {
  implicit def autoUnpack(dyn: DynamicJsValue): JsValue = dyn._value
}
