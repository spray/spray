package cc.spray

import akka.util.Logging
import akka.actor.Actor
import http._
import HttpStatusCodes._

class DetachedRouteActor(route: Route) extends Actor with Logging {
  
  protected def receive = {
    case ctx: RequestContext => {
      try {
        route(ctx)
      } catch {
        case e: Exception => ctx.respond(responseForException(ctx.request, e))
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