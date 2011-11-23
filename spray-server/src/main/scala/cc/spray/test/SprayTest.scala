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
import akka.util.Duration
import akka.util.duration._
import utils._

/**
 * Mix this trait into the class or trait containing your route and service tests.
 * Use the {{test}} and {{testService}} methods to test the behavior of your routes and services for different HTTP
 * request examples.
 */
trait SprayTest extends RouteResultComponent {

  def test(request: HttpRequest, timeout: Duration = 1000.millis)(route: Route): RoutingResultWrapper = {
    val routeResult = new RouteResult
    route {
      RequestContext(
        request = request,
        responder = routeResult.requestResponder,
        unmatchedPath = request.path
      )
    }
    // since the route might detach we block until the route actually completes or times out
    routeResult.awaitResult(timeout)
    new RoutingResultWrapper(routeResult, timeout)
  }

  trait ServiceTest extends HttpServiceLogic with Logging {
    override lazy val log: Log = NoLog // in the tests we don't log
    val customRejectionHandler = emptyPartialFunc
  }

  /**
   * The default implicit service wrapper using the HttpServiceLogic for testing.
   * If you have derived your own CustomHttpServiceLogic that you would like to test, create an implicit conversion
   * similar to this:
   * {{{
   * implicit def customWrapRootRoute(rootRoute: Route): ServiceTest = new CustomHttpServiceLogic with ServiceTest {
   *   val route = rootRoute
   * }
   * }}}
   */
  implicit def wrapRootRoute(rootRoute: Route): ServiceTest = new ServiceTest {
    val route = rootRoute
  }

  def testService(request: HttpRequest, timeout: Duration = 1000.millis)(service: ServiceTest): ServiceResultWrapper = {
    val routeResult = new RouteResult
    service.handle {
      RequestContext(
        request = request,
        responder = routeResult.requestResponder,
        unmatchedPath = request.path
      )
    }
    // since the route might detach we block until the route actually completes or times out
    routeResult.awaitResult(timeout)
    new ServiceResultWrapper(routeResult, timeout)
  }

  class ServiceResultWrapper(routeResult: RouteResult, timeout: Duration) {
    def handled: Boolean = routeResult.synchronized { routeResult.response.isDefined }
    def response: HttpResponse = routeResult.synchronized {
      routeResult.response.getOrElse {
        routeResult.rejections.foreach(rejs => doFail("Service did not convert rejection(s): " + rejs))
        doFail("Request was neither completed nor rejected within " + timeout)
      }
    }
    def chunks: List[MessageChunk] = routeResult.synchronized { routeResult.chunks.toList }
    def closingExtensions = routeResult.synchronized { routeResult.closingExtensions }
    def trailer = routeResult.synchronized { routeResult.trailer }
  }

  class RoutingResultWrapper(routeResult: RouteResult, timeout: Duration)
    extends ServiceResultWrapper(routeResult, timeout){
    override def response: HttpResponse = routeResult.synchronized {
      routeResult.response.getOrElse {
        doFail("Request was rejected with " + rejections)
      }
    }
    def rawRejections: Set[Rejection] = routeResult.synchronized {
      routeResult.rejections.getOrElse {
        routeResult.response.foreach(resp => doFail("Request was not rejected, response was " + resp))
        doFail("Request was neither completed nor rejected within " + timeout)
      }
    }
    def rejections: Set[Rejection] = Rejections.applyCancellations(rawRejections)
  }

  def doFail(msg: String): Nothing = {
    try {
      this.asInstanceOf[{ def fail(msg: String): Nothing }].fail(msg)
    } catch {
      case e: NoSuchMethodException => {
        try {
          this.asInstanceOf[{ def failure(msg: String): Nothing }].failure(msg)
        } catch {
          case e: NoSuchMethodException =>
            throw new RuntimeException("Illegal mixin: the SprayTest trait can only be mixed into test classes that " +
              "supply a fail(String) or failure(String) method (e.g. ScalaTest, Specs or Specs2 specifications)")
        }
      }
    }
  }
}
