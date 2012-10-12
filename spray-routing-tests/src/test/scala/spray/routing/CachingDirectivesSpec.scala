/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing

import spray.routing.directives.CachingDirectives
import spray.caching.LruCache
import spray.http._
import spray.util._
import HttpHeaders.`Cache-Control`
import CacheDirectives._


class CachingDirectivesSpec extends RoutingSpec with CachingDirectives {
  sequential

  val countingService = {
    var i = 0
    cache(routeCache()) {
      _.complete {
        i += 1
        i.toString
      }
    }
  }
  val errorService = {
    var i = 0
    cache(routeCache()) { _.complete { i += 1; HttpResponse(500 + i) } }
  }
  def prime(route: Route) = make(route) { _(RequestContext(HttpRequest(), system.deadLetters)) }

  "the cacheResults directive" should {
    "return and cache the response of the first GET" in {
      Get() ~> countingService ~> check { entityAs[String] === "1" }
    }
    "return the cached response for a second GET" in {
      Get() ~> prime(countingService) ~> check { entityAs[String] === "1" }
    }
    "return the cached response also for HttpFailures on GETs" in {
      Get() ~> prime(errorService) ~> check { response === HttpResponse(501) }
    }
    "not cache responses for PUTs" in {
      Put() ~> prime(countingService) ~> check { entityAs[String] === "2" }
    }
    "not cache responses for GETs if the request contains a `Cache-Control: no-cache` header" in {
      Get() ~> addHeader(`Cache-Control`(`no-cache`)) ~> prime(countingService) ~> check { entityAs[String] === "3" }
    }
    "not cache responses for GETs if the request contains a `Cache-Control: max-age=0` header" in {
      Get() ~> addHeader(`Cache-Control`(`max-age`(0))) ~> prime(countingService) ~> check { entityAs[String] === "4" }
    }
  }

}