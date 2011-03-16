package cc.spray

import http._

case class RequestContext(request: HttpRequest, responder: RoutingResult => Unit, unmatchedPath: String) {
  
  def withRequestTransformed(f: HttpRequest => HttpRequest): RequestContext = {
    copy(request = f(request))
  }

  def withHttpResponseTransformed(f: HttpResponse => HttpResponse): RequestContext = {
    withRoutingResultTransformed {
      _ match {
        case Right(response) => Right(f(response))
        case x@ Left(_) => x
      }
    }
  }
  
  def withRoutingResultTransformed(f: RoutingResult => RoutingResult): RequestContext = {
    withResponder { rr => responder(f(rr)) }
  }

  def withResponder(newResponder: RoutingResult => Unit) = copy(responder = newResponder)

  def reject(rejections: Rejection*) { responder(Left(Set(rejections: _*))) }

  def complete(obj: Any) { complete(ObjectContent(obj)) }

  def complete(content: HttpContent) { complete(HttpResponse(content = content)) }

  def complete(response: HttpResponse) { complete(Right(response)) }
  
  def complete(rr: RoutingResult) {
    if (unmatchedPath.isEmpty)
      responder(rr)
    else
      reject()
  }
  
  // can be cached
  def fail(failure: HttpFailure, reason: String = "") {
    fail(HttpStatus(failure, reason))
  }
  
  def fail(failure: HttpStatus) {
    responder(Right(HttpResponse(failure)))
  }
}

object RequestContext {
  def apply(request: HttpRequest, responder: RoutingResult => Unit = { _ => }): RequestContext = {
    apply(request, responder, request.path)
  }
}