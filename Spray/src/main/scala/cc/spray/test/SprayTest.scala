package cc.spray
package test

import cc.spray.RequestContext
import http._

trait SprayTest {
  this: { def fail(msg: String): Nothing } =>

  def test(request: HttpRequest)(route: Route): RoutingResultWrapper = {
    var result: Option[RoutingResult] = None;
    route(RequestContext(request, {ctx => result = Some(ctx)}))
    new RoutingResultWrapper(result.getOrElse(fail("No response received")))
  }

  class RoutingResultWrapper(rr: RoutingResult) {
    def handled: Boolean = rr.isRight    
    def response: HttpResponse = rr match {
      case Right(response) => response
      case Left(rejections) => fail("Request was not handled, rejections: " + rejections)
    }
  } 

  def captureRequestContext(route: (Route => Route) => Unit): RequestContext = {
    var result: Option[RequestContext] = None;
    route { inner => { ctx => { result = Some(ctx); inner(ctx) }}}
    result.getOrElse(fail("No RequestContext received"))
  }
  
  def failure(code: HttpStatusCode, reason: String): HttpResponse = HttpResponse(HttpStatus(code, reason))
} 