package cc.spray
package builders

private[spray] trait FilterBuilders {
  
  def filter (filter: RouteFilter0) = new FilterRoute0(filter)
  def filter1(filter: RouteFilter1) = new FilterRoute1(filter)
  def filter2(filter: RouteFilter2) = new FilterRoute2(filter)
  def filter3(filter: RouteFilter3) = new FilterRoute3(filter)
  def filter4(filter: RouteFilter4) = new FilterRoute4(filter)
  def filter5(filter: RouteFilter5) = new FilterRoute5(filter)
}

abstract class FilterRoute[T](val filter: RouteFilter[T]) { self =>
  protected def fromRouting(f: T => Route): Route = { ctx =>
    filter(ctx) match {
      case Right(args) => f(args)(ctx) 
      case Left(rejections) => ctx.reject(rejections: _*)
    }
  }
  protected def chainFilterWith(other: RouteFilter[T]): RouteFilter[T] = { ctx =>
    self.filter(ctx) match {
      case x@ Right(_) => x
      case Left(rejections1) => other(ctx) match {
        case x@ Right(_) => x
        case Left(rejections2) => Left(rejections1 ++ rejections2) 
      }
    }
  } 
}

class FilterRoute0(filter: RouteFilter0) extends FilterRoute[Unit](filter)
                                         with (Route => Route) {
  def apply(route: Route) = fromRouting(_ => route) 
  def | (other: FilterRoute0) = new FilterRoute0(chainFilterWith(other.filter))
}

class FilterRoute1(filter: RouteFilter1) extends FilterRoute[String](filter)
                                         with ((String => Route) => Route) {
  def apply(routing: String => Route) = fromRouting(routing(_))
  def | (other: FilterRoute1) = new FilterRoute1(chainFilterWith(other.filter))
}

class FilterRoute2(filter: RouteFilter2) extends FilterRoute[(String, String)](filter)
                                         with (((String, String) => Route) => Route) {
  def apply(routing: (String, String) => Route) = fromRouting(t => routing(t._1, t._2))
  def | (other: FilterRoute2) = new FilterRoute2(chainFilterWith(other.filter))
}

class FilterRoute3(filter: RouteFilter3) extends FilterRoute[(String, String, String)](filter)
                                         with (((String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String) => Route) = fromRouting(t => routing(t._1, t._2, t._3))
  def | (other: FilterRoute3) = new FilterRoute3(chainFilterWith(other.filter))
}

class FilterRoute4(filter: RouteFilter4) extends FilterRoute[(String, String, String, String)](filter)
                                         with (((String, String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String, String) => Route) = fromRouting(t => routing(t._1, t._2, t._3, t._4))
  def | (other: FilterRoute4) = new FilterRoute4(chainFilterWith(other.filter))
}

class FilterRoute5(filter: RouteFilter5) extends FilterRoute[(String, String, String, String, String)](filter)
                                         with (((String, String, String, String, String) => Route) => Route) {
  def apply(routing: (String, String, String, String, String) => Route) = fromRouting(t => routing(t._1, t._2, t._3, t._4, t._5))
  def | (other: FilterRoute5) = new FilterRoute5(chainFilterWith(other.filter))
}
