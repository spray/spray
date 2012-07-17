/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.testkit

import akka.actor.ActorSystem
import akka.util.duration._
import org.scalatest.Suite
import util.DynamicVariable
import cc.spray.routing._
import cc.spray.httpx.unmarshalling._
import cc.spray.httpx._
import cc.spray.http._
import cc.spray.util._


trait RouteTest extends RequestBuilding with RouteResultComponent {
  this: TestFrameworkInterface =>

  implicit val system = ActorSystem()
  implicit val routeTestTimeout = RouteTestTimeout(1.second)
  def log = system.log

  def cleanUp() { system.shutdown() }

  private val dynRR = new DynamicVariable[RouteResult](null)

  private def assertInCheck() {
    if (dynRR.value == null) sys.error("This value is only available inside of a `check` construct!")
  }

  def check[T](body: => T): RouteResult => T = dynRR.withValue(_)(body)

  def handled: Boolean = { assertInCheck(); dynRR.value.handled }

  def response: HttpResponse = { assertInCheck(); dynRR.value.response }
  def entity: HttpEntity = response.entity
  def entityAs[T :Unmarshaller] = entity.as[T].fold(error => failTest(error.toString), identityFunc)
  def body: HttpBody = entity.toOption.getOrElse(failTest("Response has no entity"))
  def contentType: ContentType = body.contentType
  def mediaType: ContentType = contentType.mediaType
  def headers: List[HttpHeader] = response.headers
  def header[T <: HttpHeader :ClassManifest]: Option[T] = response.header[T]
  def header(name: String): Option[HttpHeader] = response.headers.mapFind(h => if (h.name == name) Some(h) else None)
  def status: StatusCode = response.status

  def rejections: Seq[Rejection] = { assertInCheck(); dynRR.value.rejections }
  def rejection: Rejection = {
    val r = rejections
    if (r.size == 1) r.head else failTest("Expected a single rejection but got %s (%s)".format(r.size, r))
  }

  implicit def pimpHttpRequestWithTildeArrow(request: HttpRequest) = new HttpRequestWithTildeArrow(request)
  class HttpRequestWithTildeArrow(request: HttpRequest) {
    def ~> [A, B](f: A => B)(implicit ta: TildeArrow[A, B]): ta.Out = ta(request, f)
    def ~> (header: HttpHeader) = addHeader(header)(request)
  }

  private abstract class TildeArrow[A, B] {
    type Out
    def apply(request: HttpRequest, f: A => B): Out
  }

  private object TildeArrow {
    implicit val concatWithRequestTransformer = new TildeArrow[HttpRequest, HttpRequest] {
      type Out = HttpRequest
      def apply(request: HttpRequest, f: HttpRequest => HttpRequest) = f(request)
    }
    implicit def injectIntoRoute(implicit timeout: RouteTestTimeout) = new TildeArrow[RequestContext, Unit] {
      type Out = RouteResult
      def apply(request: HttpRequest, route: Route) = {
        val routeResult = new RouteResult(timeout.duration)
        route {
          RequestContext(
            request = request.parseAll.fold(sys.error, identityFunc),
            handler = routeResult.handler,
            unmatchedPath = request.path
          )
        }
        // since the route might detach we block until the route actually completes or times out
        routeResult.awaitResult
      }
    }
  }
}


trait ScalatestRouteTest extends RouteTest with ScalatestInterface {
  this: Suite =>
}

trait Specs2RouteTest extends RouteTest with Specs2Interface