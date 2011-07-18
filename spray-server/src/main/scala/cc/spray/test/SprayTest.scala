/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray
package test

import cc.spray.RequestContext
import http._
import util.DynamicVariable
import utils.{NoLog, Logging}

/**
 * Mix this trait into the class or trait containing your route and service tests.
 * Use the {{test}} and {{testService}} methods to test the behavior of your routes and services for different HTTP
 * request examples.
 */
trait SprayTest {
  this: { def fail(msg: String): Nothing } =>
  
  def test(request: HttpRequest)(route: Route): RoutingResultWrapper = {
    var result: Option[RoutingResult] = None;
    route(RequestContext(request, {ctx => result = Some(ctx)}, request.path))
    new RoutingResultWrapper(result.getOrElse(fail("No response received")))
  }

  class RoutingResultWrapper(rr: RoutingResult) {
    def handled: Boolean = rr.isInstanceOf[Respond]
    def response: HttpResponse = rr match {
      case Respond(response) => response
      case Reject(_) => fail("Request was rejected") 
    }
    def rawRejections: Set[Rejection] = rr match {
      case Respond(_) => fail("Request was not rejected")
      case Reject(rejections) => rejections 
    }
    def rejections: Set[Rejection] = Rejections.applyCancellations(rawRejections)   
  }
  
  trait ServiceTest extends HttpServiceLogic with Logging {
    override lazy val log = NoLog // in the tests we don't log
    private[SprayTest] val responder = new DynamicVariable[RoutingResult => Unit]( _ =>
      throw new IllegalStateException("SprayTest.HttpService instances can only be used with the SprayTest.test(service, request) method")
    )
    protected[spray] def responderForRequest(request: HttpRequest) = responder.value
  }

  /**
   * The default HttpServiceLogic for testing.
   * If you have derived your own CustomHttpServiceLogic that you would like to test, create an implicit conversion
   * similar to this:
   * {{{
   * implicit def customWrapRootRoute(rootRoute: Route): ServiceTest = new CustomHttpServiceLogic with ServiceTest {
   *   val route = routeRoute
   * }
   * }}}
   */
  implicit def wrapRootRoute(rootRoute: Route): ServiceTest = new ServiceTest {
    val route = rootRoute
    val setDateHeader = false
  }
  
  def testService(request: HttpRequest)(service: ServiceTest): ServiceResultWrapper = {
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
  
} 