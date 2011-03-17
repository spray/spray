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
    params.foldLeft[Either[List[Rejection], List[String]]](Right(Nil)) { (result, p) =>
      result match {
        case Right(values) => p.extract(ctx.request.queryParams) match {
          case Right(value) => Right(value :: values)
          case Left(rejection) => Left(rejection :: Nil)
        }
        case x@ Left(rejections) => p.extract(ctx.request.queryParams) match {
          case Left(rejection) => Left(rejection :: rejections)
          case _ => x
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
      case Left(rejections) => Left(rejections)
    }
  }
  
  def parameter(p: RequiredParameter) = filter { ctx =>
    ctx.request.queryParams.get(p.name) match {
      case Some(value) if value == p.requiredValue => Right(())
      case _ => Left(Nil) 
    }
  }
  
  implicit def fromString(name: String): Param = new Param(name)
  implicit def fromSymbol(name: Symbol): Param = new Param(name.name)
}

class Param(val name: String, val default: Option[String] = None) {
  def ? : Param = ? ("")
  def ? (default: String) = new Param(name, Some(default))
  def ! (requiredValue: String) = new RequiredParameter(name, requiredValue)
  def extract(paramMap: Map[String, String]): Either[Rejection, String] = {
    paramMap.get(name).orElse(default).map(Right(_)).getOrElse(Left(MissingQueryParamRejection(name)))
  }
}

class RequiredParameter(val name: String, val requiredValue: String)