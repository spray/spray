package spray.json

case class DynamicValidatedJsValue(value: Validated[JsValue]) extends Dynamic  {
  def applyDynamic(key: String)(index: Int = -1): DynamicValidatedJsValue =
    value.flatMap(_.dyn.applyDynamic(key)(index)).dyn

  def selectDynamic(key: String): DynamicValidatedJsValue =
    value.flatMap(_.dyn.selectDynamic(key)).dyn

  def as[T: JsonReader]: Validated[T] = value.flatMap(_.toValidated[T])
  def get[T: JsonReader]: T = as[T].get

  def map[R](f: DynamicJsValue => R): Validated[R] =
    value.map(v => f(v.dyn))
  def flatMap[R](f: DynamicJsValue => Validated[R]): Validated[R] =
    value.flatMap(v => f(v.dyn))
  def foreach(f: DynamicJsValue => Unit): Unit =
    value.foreach(v => f(v.dyn))
  def withFilter(p: DynamicJsValue => Boolean): DynamicValidatedJsValue =
    value.withFilter(v => p(v.dyn)).dyn
  def filter(p: DynamicJsValue => Boolean): DynamicValidatedJsValue =
    value.filter(v => p(v.dyn)).dyn
}

object DynamicValidatedJsValue {
  implicit def liftDynamic(value: Validated[DynamicJsValue]): DynamicValidatedJsValue =
    DynamicValidatedJsValue(value.map(_._value))

  implicit def autoUnpack(value: DynamicValidatedJsValue): Validated[JsValue] = value.value
}
