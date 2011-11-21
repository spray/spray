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
import java.util.concurrent.{CountDownLatch, TimeUnit}
import akka.util.Duration
import akka.util.duration._
import utils._

/**
 * Mix this trait into the class or trait containing your route and service tests.
 * Use the {{test}} and {{testService}} methods to test the behavior of your routes and services for different HTTP
 * request examples.
 */
trait SprayTest {

  def test(request: HttpRequest, timeout: Duration = 1000.millis)(route: Route): RoutingResultWrapper = {
    var result: Option[RoutingResult] = None
    val latch = new CountDownLatch(1)
    val responder = new SimpleResponder({ rr =>
      result = Some(rr)
      latch.countDown()
    })
    route(RequestContext(request = request, responder = responder, unmatchedPath = request.path))
    // since the route might detach we block until the route actually completes or times out
    latch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    new RoutingResultWrapper(result.getOrElse(doFail("No response received")))
  }

  class RoutingResultWrapper(rr: RoutingResult) {
    def handled: Boolean = rr.isInstanceOf[Respond]
    def response: HttpResponse = rr match {
      case Respond(response) => response
      case Reject(rejections) => doFail("Request was rejected with " + rejections)
    }
    def rawRejections: Set[Rejection] = rr match {
      case Respond(response) => doFail("Request was not rejected, response was " + response)
      case Reject(rejections) => rejections 
    }
    def rejections: Set[Rejection] = Rejections.applyCancellations(rawRejections)   
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
    var result: Option[RoutingResult] = None
    val latch = new CountDownLatch(1)
    val responder = new SimpleResponder({ rr =>
      result = Some(rr)
      latch.countDown()
    })
    service.handle(RequestContext(request = request, responder = responder, unmatchedPath = request.path))
    // since the route might detach we block until the route actually completes or times out
    latch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    result match {
      case Some(Respond(response)) => new ServiceResultWrapper(Some(response))
      case Some(_: Reject) => new ServiceResultWrapper(None)
      case None => doFail("No response received")
    }
  }

  class ServiceResultWrapper(responseOption: Option[HttpResponse]) {
    def handled: Boolean = responseOption.isDefined
    def response: HttpResponse = responseOption.getOrElse(doFail("Request was not handled"))
  }

  def captureRequestContext(route: (Route => Route) => Unit): RequestContext = {
    var result: Option[RequestContext] = None;
    route { inner => { ctx => { result = Some(ctx); inner(ctx) }}}
    result.getOrElse(doFail("No RequestContext received"))
  }

  private def doFail(msg: String): Nothing = {
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

object SprayTest extends SprayTest
