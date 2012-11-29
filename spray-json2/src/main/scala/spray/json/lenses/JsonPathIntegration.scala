package spray.json
package lenses

trait JsonPathIntegration { self: ScalarLenses with SeqLenses =>
  /**
   * Create a Lens from a json-path expression.
   */
  def fromPath(path: String): Lens[Seq] =
    fromPath(JsonPathParser(path))

  def fromPath(ast: JsonPath.Path): Lens[Seq] = {
    def convertPath(path: JsonPath.Path): Lens[Seq] = path match {
      case JsonPath.Root => value.toSeq
      case JsonPath.Selection(inner, proj) => convertPath(inner) / convertLens(proj)
    }
    def convertLens(proj: JsonPath.Projection): Lens[Seq] =
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
          f1(js).toEither.right.forall(_.forall(v1 => op.predicate(v1, v2)))
        }

      case JsonPath.Exists(path) =>
        js => convertPath(path).retr(js).isSuccess
    }
    def convertExpr(expr: JsonPath.Expr): JsValue => Validated[Seq[JsValue]] = expr match {
      case JsonPath.PathExpr(path) => js => convertPath(path).retr(js)
      case simple: JsonPath.SimpleExpr => js => Success(Seq(convertSimpleExpr(simple)(js)))
    }
    def convertSimpleExpr(expr: JsonPath.SimpleExpr): JsValue => JsValue = expr match {
      case JsonPath.Constant(x) => _ => x
    }

    convertPath(ast)
  }
}
