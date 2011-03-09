package cc.spray

class DetachedRouteActor(route: Route) extends RoutingActor {
  
  protected def receive = {
    case ctx: RequestContext => {
      try {
        route(ctx)
      } catch {
        case e: Exception => ctx.respond(responseForException(ctx.request, e))
      }
    } 
  }
  
} 