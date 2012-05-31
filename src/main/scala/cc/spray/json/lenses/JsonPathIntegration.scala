package cc.spray.json
package lenses

trait JsonPathIntegration { self: ScalarLenses with SeqLenses =>

  def fromPath(path: String): Projection[Seq] =
    fromPath(JsonPathParser(path))

  def fromPath(ast: JsonPath.Path): Projection[Seq] = {
    def convertPath(path: JsonPath.Path): Projection[Seq] = path match {
      case JsonPath.Root => value.toSeq
      case JsonPath.Selection(inner, proj) => convertPath(inner) / convertProjection(proj)
    }
    def convertProjection(proj: JsonPath.Projection): Projection[Seq] =
      proj match {
        case JsonPath.ByField(name) => field(name).toSeq
        case JsonPath.ByIndex(i) => element(i).toSeq
        case JsonPath.AllElements => elements
        case JsonPath.ByPredicate(pred) => filter(convertPredicate(pred))
      }
    def convertPredicate(pred: JsonPath.Predicate): JsPred = pred match {
      case op: JsonPath.BinOpPredicate =>
        val f1 = convertExpr(op.expr1)
        val f2 = convertSimpleExpr(op.expr2)

        js => {
          val v2 = f2(js)
          f1(js).right.forall(_.forall(v1 => op.predicate(v1, v2)))
        }

      case JsonPath.Exists(path) =>
        js => convertPath(path).retr(js).isRight
    }
    def convertExpr(expr: JsonPath.Expr): JsValue => Validated[Seq[JsValue]] = expr match {
      case JsonPath.PathExpr(path) => js => convertPath(path).retr(js)
      case simple: JsonPath.SimpleExpr => js => Right(Seq(convertSimpleExpr(simple)(js)))
    }
    def convertSimpleExpr(expr: JsonPath.SimpleExpr): JsValue => JsValue = expr match {
      case JsonPath.Constant(x) => _ => x
    }

    convertPath(ast)
  }
}
