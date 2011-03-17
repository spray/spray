package cc.spray
package builders

private[spray] trait FilterBuilders {
  
  def filter (filter: RouteFilter) = new FilterRoute0(filter)
  def filter1(filter: RouteFilter) = new FilterRoute1(filter)
  def filter2(filter: RouteFilter) = new FilterRoute2(filter)
  def filter3(filter: RouteFilter) = new FilterRoute3(filter)
  def filter4(filter: RouteFilter) = new FilterRoute4(filter)
  def filter5(filter: RouteFilter) = new FilterRoute5(filter)
}

sealed trait FilterResult
case class Reject(rejections: List[Rejection] = Nil) extends FilterResult
case class Pass(values: List[String] = Nil, transform: RequestContext => RequestContext = identity) extends FilterResult

abstract class FilterRoute(val filter: RouteFilter) { self =>
  protected def fromRouting(f: List[String] => Route): Route = { ctx =>
    filter(ctx) match {
      case Pass(values, transform) => f(values)(transform(ctx)) 
      case Reject(rejections) => ctx.reject(rejections: _*)
    }
  }
  protected def chainFilterWith(other: RouteFilter): RouteFilter = { ctx =>
    self.filter(ctx) match {
      case x: Pass => x
      case Reject(rejections1) => other(ctx) match {
        case x: Pass => x
        case Reject(rejections2) => Reject(rejections1 ++ rejections2) 
      }
    }
  } 
}

class FilterRoute0(filter: RouteFilter) extends FilterRoute(filter) with (Route => Route) {
  def apply(route: Route) = fromRouting(_ => route) 
  def | (other: FilterRoute0) = new FilterRoute0(chainFilterWith(other.filter))
}

class FilterRoute1(filter: RouteFilter) extends FilterRoute(filter) with ((String => Route) => Route) {
  def apply(routing: String => Route) = fromRouting {
    case a :: Nil => routing(a)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute1) = new FilterRoute1(chainFilterWith(other.filter))
}

class FilterRoute2(filter: RouteFilter) extends FilterRoute(filter) with (((String, String) => Route) => Route) {
  def apply(routing: (String, String) => Route) = fromRouting {
    case a :: b :: Nil => routing(a, b)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute2) = new FilterRoute2(chainFilterWith(other.filter))
}

class FilterRoute3(filter: RouteFilter) extends FilterRoute(filter)
                                         with (((String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String) => Route) = fromRouting {
    case a :: b :: c :: Nil => routing(a, b, c)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute3) = new FilterRoute3(chainFilterWith(other.filter))
}

class FilterRoute4(filter: RouteFilter) extends FilterRoute(filter)
                                         with (((String, String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String, String) => Route) = fromRouting {
    case a :: b :: c :: d :: Nil => routing(a, b, c, d)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute4) = new FilterRoute4(chainFilterWith(other.filter))
}

class FilterRoute5(filter: RouteFilter) extends FilterRoute(filter)
                                         with (((String, String, String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String, String, String) => Route) = fromRouting {
    case a :: b :: c :: d :: e :: Nil => routing(a, b, c, d, e)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute5) = new FilterRoute5(chainFilterWith(other.filter))
}
