package cc.spray

import akka.http.Endpoint
import akka.util.Logging
import akka.actor.{Channel, ActorRef, Actor}
import http.{HttpStatus, HttpResponse, HttpRequest}

class HttpService(val mainRoute: Route) extends RoutingActor {

  // use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher 

  protected def receive = {
    case request: HttpRequest => {
      val context = createRequestContext(request)
      try {
        mainRoute(context)
      } catch {
        case e: Exception => context.respond(responseForException(request, e))
      }
    }
  }
  
  protected def createRequestContext(request: HttpRequest) = {
    RequestContext(request, respond(request, self.channel))
  }
  
  protected def respond(request: HttpRequest, channel: Channel[Any])(responseContext: ResponseContext) {
    channel ! responseContext.response
  }  
}

object HttpService {
  def apply(mainRoute: Route) = new HttpService(mainRoute) 
}