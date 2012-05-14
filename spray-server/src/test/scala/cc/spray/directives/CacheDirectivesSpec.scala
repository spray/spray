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

package cc.spray
package directives

import util._
import http._
import HttpMethods._
import HttpHeaders._
import test.AbstractSprayTest
import caching.LruCache

class CacheDirectivesSpec extends AbstractSprayTest {
  sequential

  "the cacheResults directive" should {
    val countingService = {
      var i = 0
      cacheResults(LruCache()) {
        _.complete {
          i += 1
          i.toString
        }
      }
    }
    val errorService = {
      var i = 0
      cache { _.complete { i += 1; HttpResponse(500 + i) } }
    }
    def prime(route: Route) = make(route) { _(RequestContext(HttpRequest(GET), RequestResponder(_ => ()))) }
    
    "return and cache the response of the first GET" in {      
      test(HttpRequest(GET)) {
        countingService
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response for a second GET" in {
      test(HttpRequest(GET)) {
        prime(countingService)        
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response also for HttpFailures on GETs" in {
      test(HttpRequest(GET)) {
        prime(errorService)        
      }.response mustEqual HttpResponse(501)
    }
    "not cache responses for PUTs" in {
      test(HttpRequest(PUT)) {
        prime(countingService)        
      }.response.content.as[String] mustEqual Right("2")
    }
    import CacheDirectives._
    "not cache responses for GETs if the request contains a Cache-Control: no-cache header" in {
      test(HttpRequest(GET, headers = List(`Cache-Control`(`no-cache`)))) {
        prime(countingService)
      }.response.content.as[String] mustEqual Right("3")
    }
    "not cache responses for GETs if the request contains a Cache-Control: max-age=0 header" in {
      test(HttpRequest(GET, headers = List(`Cache-Control`(`max-age`(0))))) {
        prime(countingService)
      }.response.content.as[String] mustEqual Right("4")
    }
  }

}