package cc.spray
package test

import cc.spray.{ResponseContext, RequestContext}
import http.{HttpHeader, HttpRequest}

trait RouteTest {
  this: { def fail(msg: String): Nothing } =>

  def responseFor(request: HttpRequest)(route: Route): ResponseContext = {
    var result: Option[ResponseContext] = None;
    route(RequestContext(request, {ctx => result = Some(ctx)}))
    result.getOrElse(fail("No response received"))
  }
  
  def captureRequestContext(route: (Route => Route) => Unit): RequestContext = {
    var result: Option[RequestContext] = None;
    route { inner => { ctx => { result = Some(ctx); inner(ctx) }}}
    result.getOrElse(fail("No RequestContext received"))
  }

  implicit def pimpResponseContext1(context: ResponseContext) = new {
    def contentAsString: String = context.response
            .getOrElse(fail("Request was not handled"))
            .content.map(new String(_))
            .getOrElse(fail("Response has no content"))
  }

  implicit def pimpResponseContext2(context: ResponseContext) = new {
    def responseHeaders: List[HttpHeader] = context.response.getOrElse(fail("Request was not handled")).headers
  }

} 