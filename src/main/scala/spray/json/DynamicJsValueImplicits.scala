package spray.json

trait JsValueWithDyn {
  def dyn: DynamicJsValue
}

trait DynamicJsValueImplicits {
  implicit def jsValueHasDyn(jsValue: JsValue): JsValueWithDyn =
    new JsValueWithDyn {
      def dyn: DynamicJsValue = DynamicJsValue(jsValue)
    }
}
