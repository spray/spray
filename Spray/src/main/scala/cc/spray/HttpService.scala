package cc.spray

import akka.http.Endpoint
import akka.actor.Channel
import http._

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
  
  protected def respond(request: HttpRequest, channel: Channel[Any])(response: RoutingResult) {
    response match {
      case Right(httpResponse) => channel ! Some(httpResponse) 
      case Left(rejections) => channel ! responseForRejections(rejections) 
    }
  }
  
  protected def responseForRejections(rejections: Set[Rejection]): Option[HttpResponse] = {
    if (rejections.contains(PathMatchedRejection)) {
      if (rejections.contains(MethodRejection)) {
        Some(HttpResponse(HttpStatusCodes.MethodNotAllowed))
      } else if (rejections.contains(AcceptRejection)) {
        Some(HttpResponse(HttpStatusCodes.NotAcceptable))
      } else {
        throw new IllegalStateException("Unknown request rejection")
      }
    } else {
      None // no path matched, so signal to the root service that this service did not handle the request 
    }
  } 
}

object HttpService {
  def apply(mainRoute: Route) = new HttpService(mainRoute) 
}