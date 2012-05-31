package cc.spray.json
package lenses

object JsonLenses extends
  ScalarLenses with
  OptionLenses with
  SeqLenses with
  Operations with
  JsonPathIntegration {

  implicit def strToField(name: String): ScalarProjection = field(name)
  implicit def symbolToField(sym: Symbol): ScalarProjection = field(sym.name)

}