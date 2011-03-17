package cc.spray
package builders

import akka.actor.Actor
import akka.util.Logging
import http._
import HttpStatusCodes._

private[spray] trait DetachedBuilders {

  def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = { ctx =>
    Actor.actorOf(detachedActorFactory(route)).start ! ctx
  }
  
  // implicits  
  
  implicit def defaultDetachedActorFactory(route: Route): Actor = new DetachedRouteActor(route)
  
}

class DetachedRouteActor(route: Route) extends Actor with Logging {  
  protected def receive = {
    case ctx: RequestContext => {
      try {
        route(ctx)
      } catch {
        case e: Exception => ctx.responder(Right(responseForException(ctx.request, e)))
      }
    } 
  }
  
  protected def responseForException(request: HttpRequest, e: Exception): HttpResponse = {
    log.error("Error during processing of request {}:\n{}", request, e)
    e match {
      case e: HttpException => HttpResponse(e.status)
      case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage)) 
    }    
  }   
} 