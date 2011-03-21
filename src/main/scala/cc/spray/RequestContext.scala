package cc.spray

import http._
import marshalling.{CantMarshal, MarshalWith}

case class RequestContext(request: HttpRequest, responder: RoutingResult => Unit, unmatchedPath: String) {
  
  def withRequestTransformed(f: HttpRequest => HttpRequest): RequestContext = {
    copy(request = f(request))
  }

  def withHttpResponseTransformed(f: HttpResponse => HttpResponse): RequestContext = {
    withRoutingResultTransformed {
      _ match {
        case Respond(response) => Respond(f(response))
        case x: Reject => x
      }
    }
  }
  
  def withRoutingResultTransformed(f: RoutingResult => RoutingResult): RequestContext = {
    withResponder { rr => responder(f(rr)) }
  }

  def withResponder(newResponder: RoutingResult => Unit) = copy(responder = newResponder)

  def reject(rejections: Rejection*) { reject(Set(rejections: _*)) }
  
  def reject(rejections: Set[Rejection]) { responder(Reject(rejections)) }

  def complete[A :Marshaller](obj: A) {
    marshaller.apply(request.isContentTypeAccepted(_)) match {
      case MarshalWith(converter) => complete(converter(obj))
      case CantMarshal(onlyTo) => reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  }

  def complete(content: HttpContent) { complete(HttpResponse(content = Some(content))) }

  def complete(response: HttpResponse) { complete(Respond(response)) }
  
  def complete(rr: RoutingResult) {
    if (unmatchedPath.isEmpty) {
      responder(rr)
    } else {
      reject()
    }
  }
  
  // can be cached
  def fail(failure: HttpFailure, reason: String = "") {
    fail(HttpStatus(failure, reason))
  }
  
  def fail(failure: HttpStatus) {
    responder(Respond(HttpResponse(failure)))
  }
}

object RequestContext {
  def apply(request: HttpRequest, responder: RoutingResult => Unit = { _ => }): RequestContext = {
    apply(request, responder, request.path)
  }
}