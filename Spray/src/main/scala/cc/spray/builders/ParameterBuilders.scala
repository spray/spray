package cc.spray
package builders

import http._
import HttpStatusCodes._

private[spray] trait ParameterBuilders {
  
  def parameter(p: Param)
               (routing: String => Route): Route = build(p :: Nil) {
    case a :: Nil => routing(a)
    case _ => throw new IllegalStateException
  }
  
  def parameters(p1: Param, p2: Param)
                (routing: (String, String) => Route) = build(p1 :: p2 :: Nil) {
    case b :: a :: Nil => routing(a, b)
    case _ => throw new IllegalStateException
  }
  
  def parameters(p1: Param, p2: Param, p3: Param)
                (routing: (String, String, String) => Route) = build(p1 :: p2 :: p3 :: Nil) {
    case c :: b :: a :: Nil => routing(a, b, c)
    case _ => throw new IllegalStateException
  }
  
  def parameters(p1: Param, p2: Param, p3: Param, p4: Param)
                (routing: (String, String, String, String) => Route) = build(p1 :: p2 :: p3 :: p4 :: Nil) {
    case d :: c :: b :: a :: Nil => routing(a, b, c, d)
    case _ => throw new IllegalStateException
  }
  
  def parameters(p1: Param, p2: Param, p3: Param, p4: Param, p5: Param)
                (routing: (String, String, String, String, String) => Route) = build(p1 :: p2 :: p3 :: p4 :: p5 :: Nil) {
    case e :: d :: c :: b :: a :: Nil => routing(a, b, c, d, e)
    case _ => throw new IllegalStateException
  }
  
  private def build(params: List[Param])(f: List[String] => Route): Route = { ctx =>
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
      case Right(values) => f(values)(ctx)
      case Left(missing) => ctx.fail(NotFound, "Query parameter(s) required: " + missing.reverse.mkString(", "))
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