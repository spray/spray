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
package directives

import http._
import cache._
import HttpMethods._
import test.AbstractSprayTest
import akka.util.Duration

class CacheDirectivesSpec extends AbstractSprayTest {
  val completeFail: Route = {
    _.complete { fail(); "Fail" }
  }
  
  "the cache directive" should {
    "return and cache the response of the first GET" in {
      var i = 41
      test(HttpRequest(GET, "/answer")) {
        cache() {
          _.complete { i += 1; i.toString }
        }
      }.response.content.as[String] mustEqual Right("42")
    }
    "return the cached response for a second GET with the same uri" in {
      test(HttpRequest(GET, "/answer")) {
        cache()(completeFail)
      }.response.content.as[String] mustEqual Right("42")
    }
    "not cache responses for PUTs by default" in {
      test(HttpRequest(PUT, "/answer")) {
        cache()(completeOk)
      }.response mustBe Ok
    }
    "return the cached response also for HttpFailures on GETs" in {
      test(HttpRequest(GET, "/error")) {
        cache() {
          _.complete { HttpResponse(501) }
        }
      }.response mustEqual HttpResponse(501)
      test(HttpRequest(GET, "/error")) {
        cache()(completeFail)
      }.response mustEqual HttpResponse(501)
    }
    "use a cache named 'routes' by default" in {
      val c = DefaultCacheBackend("routes")
      c.set("/key", HttpResponse(200, "Hi"))
      test(HttpRequest(GET,"/key")) {
        cache()(completeFail)
      }.response.content.as[String] mustEqual Right("Hi")
    }
  }
  "the cache directive" can {
    "cache for other HttpMethods when configured to" in {
      test(HttpRequest(GET, "/fight/http")) {
        cache()(completeOk)
      }.response mustBe Ok
      test(HttpRequest(POST, "/fight/http")) {
        cache(policy = c => Some(c.request.uri))(completeFail)
      }.response mustBe Ok
    }
    "use custom policy and keyer function" in {
      test(HttpRequest(GET, "/answer")) {
        cache()(completeFail)
      }.response.content.as[String] mustEqual Right("42")
      test(HttpRequest(GET, "/answer")) {
        cache(policy = c => Some("prefix" + c.request.uri))(completeOk)
      }.response mustBe Ok
    }
    "use a custom cache by name or cache argument" in {
      val c = DefaultCacheBackend("myCache")
      test(HttpRequest(GET, "/answer")) {
        cache(name="myCache")(completeOk)
      }.response mustBe Ok
      test(HttpRequest(GET, "/answer")) {
        cacheIn(c)(completeFail)
      }.response mustBe Ok
    }
    "use a custom cache backend" in {
      class MyCache(name: String) extends DummyCache(name) {
        val answer = HttpResponse(StatusCodes.OK, "Yay")
        override def get(k: Any) = Some(answer)
        override def apply(k: Any, ttl: Option[Duration] = None)(v: =>Any) = answer
      }
      object MyCache extends CacheBackend {
        type CacheType = MyCache
        def apply(name: String, config: Map[String, String]=Map.empty) = new MyCache(name)
      }
      // use default cache of the custom backend
      test(HttpRequest(GET)) {
        cache(backend=MyCache)(completeFail)
      }.response.content.as[String] mustEqual Right("Yay")
      // use named cache of the custom backend
      test(HttpRequest(GET)) {
        cache(name="routes", backend=MyCache)(completeFail)
      }.response.content.as[String] mustEqual Right("Yay")
      // specify cache using the implicit police
      test(HttpRequest(GET)) {
        cacheIn(MyCache("routes"))(completeFail)
      }.response.content.as[String] mustEqual Right("Yay")
    }
  }
  "CacheKeyers.DefaultPolicy" should {
    import CacheKeyers._
    def req(m: HttpMethod, uri: String="") = RequestContext(HttpRequest(m, uri))
    "by default filter get requests and cache on request uri" in {
      DefaultCacheKeyProvider(req(HEAD,"/uri")) mustBe None
      DefaultCacheKeyProvider(req(GET,"/uri")) mustEqual Some("/uri")
    }
    "build a new policy with an additional filter" in {
      val policy = DefaultCacheKeyProvider & { _.request.uri.last == '/' }
      policy(req(GET,"/uri/")) mustEqual Some("/uri/")
      policy(req(GET,"/uri")) mustBe None
      policy(req(HEAD,"/uri/")) mustBe None
    }
  }
}

