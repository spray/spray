package cc.spray.json
package lenses

/**
 * An aggregate option to import all of the functionality of JsonLenses with one
 * import.
 */
object JsonLenses extends
  ScalarLenses with
  OptionLenses with
  SeqLenses with
  Operations with
  JsonPathIntegration with
  ExtraImplicits {

  implicit def strToField(name: String): ScalarLens = field(name)
  implicit def symbolToField(sym: Symbol): ScalarLens = field(sym.name)
}