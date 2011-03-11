package cc.spray
package test

import cc.spray.RequestContext
import http._
import util.DynamicVariable

trait SprayTest {
  this: { def fail(msg: String): Nothing } =>
  
  def test(request: HttpRequest)(route: Route): RoutingResultWrapper = {
    var result: Option[RoutingResult] = None;
    route(RequestContext(request, {ctx => result = Some(ctx)}))
    new RoutingResultWrapper(result.getOrElse(fail("No response received")))
  }

  class RoutingResultWrapper(rr: RoutingResult) {
    def handled: Boolean = rr.isRight
    def response: HttpResponse = rr.right.getOrElse(fail("Request was rejected"))
    def rejections: Set[Rejection] = rr.left.getOrElse(fail("Request was not rejected"))
  }
  
  trait ServiceTest extends HttpServiceLogic {
    private[SprayTest] val responder = new DynamicVariable[RoutingResult => Unit]( _ =>
      throw new IllegalStateException("SprayTest.HttpService instances can only be used with the SprayTest.test(service, request) method")
    )
    protected[spray] def responderForRequest(request: HttpRequest) = responder.value
  }

  /**
   * The default HttpServiceLogic for testing.
   * If you have derived your own CustomHttpServiceLogic that you would like to test, use this construct:
   * val serviceTest = new CustomHttpServiceLogic with ServiceTest {
   *    val route = ...
   * }
   */
  case class HttpServiceTest(route: Route) extends ServiceTest
  
  def test(service: ServiceTest, request: HttpRequest): ServiceResultWrapper = {
    var response: Option[Option[HttpResponse]] = None 
    service.responder.withValue(rr => { response = Some(service.responseFromRoutingResult(rr)) }) {
      service.handle(request)
    }
    new ServiceResultWrapper(response.getOrElse(fail("No response received")))
  }
  
  class ServiceResultWrapper(responseOption: Option[HttpResponse]) {
    def handled: Boolean = responseOption.isDefined
    def response: HttpResponse = responseOption.getOrElse(fail("Request was not handled"))
  }

  def captureRequestContext(route: (Route => Route) => Unit): RequestContext = {
    var result: Option[RequestContext] = None;
    route { inner => { ctx => { result = Some(ctx); inner(ctx) }}}
    result.getOrElse(fail("No RequestContext received"))
  }
  
  def failure(code: HttpStatusCode, reason: String = ""): HttpResponse = HttpResponse(HttpStatus(code, reason))
} 