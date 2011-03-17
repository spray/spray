package cc.spray
package builders

private[spray] trait FilterBuilders {
  
  def filter (filter: RouteFilter0) = new FilterRoute0(filter)
  def filter1(filter: RouteFilter1) = new FilterRoute[String](filter)
  def filter2(filter: RouteFilter2) = new FilterRoute[(String, String)](filter)
  def filter3(filter: RouteFilter3) = new FilterRoute[(String, String, String)](filter)
  def filter4(filter: RouteFilter4) = new FilterRoute[(String, String, String, String)](filter)
  def filter5(filter: RouteFilter5) = new FilterRoute[(String, String, String, String, String)](filter)
}

// unfortunately we need a special treatment for FilterRoute0 since its routing argument does not take any parameters
class FilterRoute0(val filter: RouteFilter0) extends (Route => Route) { self =>    
  def apply(route: Route) = { ctx =>
    filter(ctx) match {
      case None => route(ctx)
      case Some(rejections) => ctx.reject(rejections: _*)
    }
  }
  def | (other: FilterRoute0) = new FilterRoute0( ctx =>
    self.filter(ctx) match {
      case None => None
      case Some(rejections1) => other.filter(ctx) match {
        case None => None
        case Some(rejections2) => Some(rejections1 ++ rejections2) 
      }
    }
  )
}

class FilterRoute[T](val filter: RouteFilter[T]) extends ((T => Route) => Route) { self =>
  def apply(routing: T => Route) = { ctx =>
    filter(ctx) match {
      case Right(args) => routing(args)(ctx) 
      case Left(rejections) => ctx.reject(rejections: _*)
    }
  }
  def | (other: FilterRoute[T]) = new FilterRoute[T]( ctx =>
    self.filter(ctx) match {
      case x@ Right(_) => x
      case Left(rejections1) => other.filter(ctx) match {
        case x@ Right(_) => x
        case Left(rejections2) => Left(rejections1 ++ rejections2) 
      }
    }
  )
}
