package spray.json

trait FieldName[T] {
  def toFieldName(t: T): String
  def fromFieldName(name: String): T
}

object FieldName {
  def get[T: FieldName](t: T): String = implicitly[FieldName[T]].toFieldName(t)
  def convert[T: FieldName](name: String): T = implicitly[FieldName[T]].fromFieldName(name)

  implicit val StringIsFieldName: FieldName[String] = new FieldName[String] {
    def toFieldName(t: String): String = t
    def fromFieldName(name: String): String = name
  }
  implicit val SymbolIsFieldName: FieldName[Symbol] = new FieldName[Symbol] {
    def toFieldName(t: Symbol): String = t.name
    def fromFieldName(name: String): Symbol = Symbol(name)
  }
}
