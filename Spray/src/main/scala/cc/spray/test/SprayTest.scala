package cc.spray
package test

import cc.spray.{ResponseContext, RequestContext}
import http._

trait SprayTest {
  this: { def fail(msg: String): Nothing } =>

  def test(request: HttpRequest)(route: Route): ContextWrapper = {
    var result: Option[ResponseContext] = None;
    route(RequestContext(request, {ctx => result = Some(ctx)}))
    new ContextWrapper(result.getOrElse(fail("No response received")))
  }

  class ContextWrapper(context: ResponseContext) {
    def handled: Boolean = context.response.isDefined
    def response: HttpResponse = context.response.getOrElse(fail("Request was not handled"))
  } 

  def captureRequestContext(route: (Route => Route) => Unit): RequestContext = {
    var result: Option[RequestContext] = None;
    route { inner => { ctx => { result = Some(ctx); inner(ctx) }}}
    result.getOrElse(fail("No RequestContext received"))
  }
  
  // for easy creation of HttpResponse content
  implicit def stringToOptionByteArray(string: String) = Some(string.getBytes)
} 