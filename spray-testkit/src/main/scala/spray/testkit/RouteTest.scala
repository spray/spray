/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.testkit

import com.typesafe.config.{ ConfigFactory, Config }
import scala.util.DynamicVariable
import akka.actor.ActorSystem
import org.scalatest.Suite
import spray.routing.directives.ExecutionDirectives
import spray.routing._
import spray.httpx.unmarshalling._
import spray.httpx._
import spray.http._
import spray.util._
import akka.util.NonFatal

trait RouteTest extends RequestBuilding with RouteResultComponent {
  this: TestFrameworkInterface ⇒

  def testConfigSource: String = ""
  def testConfig: Config = {
    val source = testConfigSource
    val config = if (source.isEmpty) ConfigFactory.empty() else ConfigFactory.parseString(source)
    config.withFallback(ConfigFactory.load())
  }
  implicit val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConfig)
  implicit def executor = system.dispatcher

  def cleanUp() { system.shutdown() }

  private val dynRR = new DynamicVariable[RouteResult](null)

  private def assertInCheck() {
    if (dynRR.value == null) sys.error("This value is only available inside of a `check` construct!")
  }

  def check[T](body: ⇒ T): RouteResult ⇒ T = dynRR.withValue(_)(body)

  def result = { assertInCheck(); dynRR.value }
  def handled: Boolean = result.handled
  def response: HttpResponse = result.response
  def entity: HttpEntity = response.entity
  def entityAs[T: Unmarshaller: ClassManifest]: T = entity.as[T].fold(error ⇒ failTest("Could not unmarshal response " +
    "to type '" + classManifest[T] + "' for `entityAs` assertion: " + error + "\n\nResponse was: " + response), identityFunc)
  def body: HttpBody = entity.toOption getOrElse failTest("Response has no body")
  def contentType: ContentType = body.contentType
  def mediaType: MediaType = contentType.mediaType
  def charset: HttpCharset = contentType.charset
  def definedCharset: Option[HttpCharset] = contentType.definedCharset
  def headers: List[HttpHeader] = response.headers
  def header[T <: HttpHeader: ClassManifest]: Option[T] = response.header[T]
  def header(name: String): Option[HttpHeader] = response.headers.mapFind(h ⇒ if (h.name == name) Some(h) else None)
  def status: StatusCode = response.status
  def chunks: List[MessageChunk] = result.chunks
  def closingExtension: String = result.closingExtension
  def trailer: List[HttpHeader] = result.trailer

  def rejections: List[Rejection] = {
    assertInCheck()
    RejectionHandler.applyTransformations(dynRR.value.rejections)
  }
  def rejection: Rejection = {
    val r = rejections
    if (r.size == 1) r.head else failTest("Expected a single rejection but got %s (%s)".format(r.size, r))
  }

  implicit def pimpHttpRequestWithTildeArrow(request: HttpRequest) = new HttpRequestWithTildeArrow(request)
  class HttpRequestWithTildeArrow(request: HttpRequest) {
    def ~>[A, B](f: A ⇒ B)(implicit ta: TildeArrow[A, B]): ta.Out = ta(request, f)
    def ~>(header: HttpHeader) = addHeader(header)(request)
  }

  private abstract class TildeArrow[A, B] {
    type Out
    def apply(request: HttpRequest, f: A ⇒ B): Out
  }

  private object TildeArrow {
    implicit val concatWithRequestTransformer = new TildeArrow[HttpRequest, HttpRequest] {
      type Out = HttpRequest
      def apply(request: HttpRequest, f: HttpRequest ⇒ HttpRequest) = f(request)
    }
    implicit def injectIntoRoute(implicit timeout: RouteTestTimeout, settings: RoutingSettings, log: LoggingContext) =
      new TildeArrow[RequestContext, Unit] {
        type Out = RouteResult
        def apply(request: HttpRequest, route: Route) = {
          val routeResult = new RouteResult(timeout.duration)
          val effectiveRequest =
            try request.withEffectiveUri(securedConnection = false)
            catch { case NonFatal(_) ⇒ request }
          ExecutionDirectives.handleExceptions(ExceptionHandler.default)(route) {
            RequestContext(
              request = effectiveRequest,
              responder = routeResult.handler,
              unmatchedPath = effectiveRequest.uri.path)
          }
          // since the route might detach we block until the route actually completes or times out
          routeResult.awaitResult
        }
      }
  }
}

trait ScalatestRouteTest extends RouteTest with ScalatestInterface { this: Suite ⇒ }

trait Specs2RouteTest extends RouteTest with Specs2Interface
