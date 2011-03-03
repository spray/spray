package cc.spray

import http._

case class Context(request: HttpRequest, responder: HttpResponse => Unit) {
  
  def withTransformedRequest(transformer: HttpRequest => HttpRequest): Context = {
    copy(request = transformer(request))
  }
  
  def withResponseTransformer(transformer: HttpResponse => HttpResponse): Context = {
    copy(responder = response => responder(transformer(response)))
  }
  
  def withResponseHeader(header: HttpHeader): Context = withResponseTransformer { response =>
    response.copy(headers = header :: response.headers.filterNot(_.name == header.name))
  }
  
  def respond(string: String): Boolean = { respond(string.getBytes) }

  def respond(array: Array[Byte]): Boolean = { respond(HttpResponse(content = Some(array))) }

  def respond(response: HttpResponse): Boolean = { responder(response); true }
}