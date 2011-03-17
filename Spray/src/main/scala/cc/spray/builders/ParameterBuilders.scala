package cc.spray
package builders

private[spray] trait ParameterBuilders {
  this: FilterBuilders =>
  
  def parameter (a: Param) = filter1(build(a :: Nil))
  def parameters(a: Param, b: Param) = filter2(build(a :: b :: Nil))
  def parameters(a: Param, b: Param, c: Param) = filter3(build(a :: b :: c :: Nil))
  def parameters(a: Param, b: Param, c: Param, d: Param) = filter4(build(a :: b :: c :: d :: Nil))
  def parameters(a: Param, b: Param, c: Param, d: Param, e: Param) = filter5(build(a :: b :: c :: d :: e :: Nil))
  
  private def build[T](params: List[Param]): RouteFilter[T] = { ctx =>
    params.foldLeft[Either[List[String], List[String]]](Right(Nil)) { (result, p) =>
      result match {
        case Right(values) => p.extract(ctx.request.queryParams) match {
          case Some(value) => Right(value :: values)
          case None => Left(p.name :: Nil)
        }
        case x@ Left(missing) => p.extract(ctx.request.queryParams) match {
          case Some(_) => x
          case None => Left(p.name :: missing)
        }
      }
    } match {
      case Right(values) => values match {
        case a :: Nil => Right(a.asInstanceOf[T])
        case a :: b :: Nil => Right((b, a).asInstanceOf[T])
        case a :: b :: c :: Nil => Right((c, b, a).asInstanceOf[T])
        case a :: b :: c :: d :: Nil => Right((d, c, b, a).asInstanceOf[T])
        case a :: b :: c :: d :: e :: Nil => Right((e, d, c, b, a).asInstanceOf[T])
        case _ => throw new IllegalStateException
      }
      case Left(missing) => Left(missing.reverse.map(QueryParamRequiredRejection(_)))
    }
  }
  
  implicit def fromString(name: String): Param = new Param(name)
  implicit def fromSymbol(name: Symbol): Param = new Param(name.name)
}

class Param(val name: String, val default: Option[String] = None) {
  def ? : Param = ? ("")
  def ? (default: String) = new Param(name, Some(default))
  def extract(paramMap: Map[String, String]): Option[String] = paramMap.get(name).orElse(default)
}