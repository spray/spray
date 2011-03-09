package cc.spray

import http._

case class RequestContext(request: HttpRequest, responder: ResponseContext => Unit, unmatchedPath: String) {
  
  def withTransformedRequest(transformer: HttpRequest => HttpRequest): RequestContext = {
    copy(request = transformer(request))
  }

  def withResponseTransformer(transformer: HttpResponse => Option[HttpResponse]): RequestContext = {
    withResponseContextTransformer { responseContext =>
      responseContext.response match {
        case Some(res) => responseContext.copy(response = transformer(res))
        case None => responseContext
      }
    }
  }

  def withResponseContextTransformer(transformer: ResponseContext => ResponseContext): RequestContext = {
    withResponder { responseContext => responder(transformer(responseContext)) }
  }

  def withResponder(newResponder: ResponseContext => Unit) = copy(responder = newResponder)
  
  def respond(string: String) { respond(string.getBytes) }

  def respond(array: Array[Byte]) { respond(HttpResponse(content = Some(array))) }

  def respond(response: HttpResponse) { responder(ResponseContext(Some(response))) }
  
  def respond(responseContext: ResponseContext) { responder(responseContext) }
  
  def respondUnhandled { respond(ResponseContext(None)) }
  
  def fail(failure: HttpFailure, reason: String = "") {
    respond(HttpResponse(HttpStatus(failure, reason)))
  }
}

object RequestContext {
  def apply(request: HttpRequest, responder: ResponseContext => Unit): RequestContext = {
    apply(request, responder, request.path)
  }
}