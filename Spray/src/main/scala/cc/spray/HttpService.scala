package cc.spray

import akka.http.Endpoint
import akka.util.Logging
import http.{HttpResponse, HttpRequest}
import akka.actor.{ActorRef, Actor}

class HttpService(val mainRoute: Route) extends Actor with Logging {

  // use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher 

  protected def receive = {
    case request: HttpRequest => mainRoute(createRequestContext(request))
  }
  
  protected def createRequestContext(request: HttpRequest) = {
    RequestContext(request, respond(request, self.sender.get))
  }
  
  protected def respond(request: HttpRequest, sender: ActorRef)(responseContext: ResponseContext) {
    sender ! responseContext.response
  }
}

object HttpService {
  def apply(mainRoute: Route) = new HttpService(mainRoute) 
}