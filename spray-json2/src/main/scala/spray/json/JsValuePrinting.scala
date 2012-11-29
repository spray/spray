package spray.json

case class JsValuePrinting(value: JsValue) {
  def toString(printer: (JsValue => String)) = printer(value)
  def formatCompact = CompactFormatter(value)
  def formatPretty = PrettyFormatter(value)
}

trait JsValuePrintingImplicits {
  implicit def jsValueHasPrintingMethods(value: JsValue): JsValuePrinting =
    JsValuePrinting(value)
}