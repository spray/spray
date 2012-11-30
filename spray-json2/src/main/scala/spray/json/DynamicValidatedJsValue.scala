package spray.json

case class DynamicValidatedJsValue(value: Validated[JsValue]) extends Dynamic  {
  def applyDynamic(key: String)(index: Int = -1): DynamicValidatedJsValue =
    value.flatMap(_.dyn.applyDynamic(key)(index)).dyn

  def selectDynamic(key: String): DynamicValidatedJsValue =
    value.flatMap(_.dyn.selectDynamic(key)).dyn
}

object DynamicValidatedJsValue {
  implicit def liftDynamic(value: Validated[DynamicJsValue]): DynamicValidatedJsValue =
    DynamicValidatedJsValue(value.map(_._value))

  implicit def autoUnpack(value: DynamicValidatedJsValue): Validated[JsValue] = value.value
}