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
import akka.util.Duration
import akka.util.duration._
import cc.spray.routing._
import cc.spray.httpx._
import cc.spray.http._


trait RouteTest extends RequestBuilding with RouteResultComponent {
  this: TestFrameworkInterface =>

  implicit val system = ActorSystem()
  implicit val routeTestTimeout = RouteTestTimeout(1.second)
  def log = system.log

  implicit def request2RouteInjectableHttpRequest(request: HttpRequest) = new RouteInjectableHttpRequest(request)
  class RouteInjectableHttpRequest(request: HttpRequest) {
    def ~> (route: Route)(implicit timeout: RouteTestTimeout): RouteResult = {
      val routeResult = new RouteResult(timeout.duration)
      route {
        RequestContext(
          request = request,
          handler = routeResult.handler,
          unmatchedPath = request.path
        )
      }
      // since the route might detach we block until the route actually completes or times out
      routeResult.awaitResult
    }
  }
}
