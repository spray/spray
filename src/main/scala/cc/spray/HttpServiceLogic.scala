package cc.spray

import http._
import HttpStatusCodes._

trait HttpServiceLogic {
  
  def route: RootRoute
  
  def handle(request: HttpRequest) {
    val context = contextForRequest(request)
    try {
      route(context)
    } catch {
      case e: Exception => context.responder(Respond(responseForException(request, e)))
    }
  }
  
  protected[spray] def contextForRequest(request: HttpRequest): RequestContext = {
    RequestContext(request, responderForRequest(request))
  }
  
  protected[spray] def responderForRequest(request: HttpRequest): RoutingResult => Unit
  
  protected[spray] def responseFromRoutingResult(rr: RoutingResult): Option[HttpResponse] = rr match {
    case Respond(httpResponse) => Some(httpResponse) 
    case Reject(rejections) => responseForRejections(rejections.toSet) 
  }
  
  protected[spray] def responseForRejections(rejections: Set[Rejection]): Option[HttpResponse] = {
    if (rejections.contains(PathMatchedRejection)) {
      val methodRejections = rejections.collect { case MethodRejection(method) => method } 
      if (!methodRejections.isEmpty) {
        // TODO: add Allow header (required by the spec)
        Some(HttpResponse(HttpStatus(MethodNotAllowed, "HTTP method not allowed, supported methods: " +
                methodRejections.mkString(", "))))
      } else {
        val queryParamRequiredRejections = rejections.collect { case MissingQueryParamRejection(p) => p } 
        if (!queryParamRequiredRejections.isEmpty) {
          Some(HttpResponse(HttpStatus(NotFound, "Request is missing the following required query parameters: " +
                queryParamRequiredRejections.mkString(", "))))
        } else {
          throw new IllegalStateException("Unknown request rejection")
        }
      }
    } else {
      None // no path matched, so signal to the root service that this service did not handle the request 
    }
  }
  
  protected[spray] def responseForException(request: HttpRequest, e: Exception): HttpResponse = e match {
    case e: HttpException => HttpResponse(e.status)
    case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage))
  } 
}

class RootRoute private[spray](val route: Route) extends (RequestContext => Unit) {
  def apply(context: RequestContext) { route(context) }
}