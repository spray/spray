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

import spray.http._
import StatusCodes._
import HttpHeaders._
import MediaTypes._
import HttpMethods._


class MiscDirectivesSpec extends RoutingSpec {

  "respondWithStatus" should {
    "set the given status on successful responses" in {
      Get() ~> {
        respondWithStatus(Created) { completeOk }
      } ~> check { response === HttpResponse(Created) }
    }
    "leave rejections unaffected" in {
      Get() ~> {
        respondWithStatus(Created) { reject() }
      } ~> check { rejections === Nil }
    }
  }
  
  "respondWithHeader" should {
    val customHeader = RawHeader("custom", "custom")
    "add the given headers to successful responses" in {
      Get() ~> {
        respondWithHeader(customHeader) { completeOk }
      } ~> check { response === HttpResponse(headers = customHeader :: Nil) }
    }
    "leave rejections unaffected" in {
      Get() ~> {
        respondWithHeader(customHeader) { reject() }
      } ~> check { rejections === Nil }
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      Get() ~> {
        get { complete("first") } ~ get { complete("second") }
      } ~> check { entityAs[String] === "first" }
    }
    "yield the second sub route if the first did not succeed" in {
      Get() ~> {
        post { complete("first") } ~ get { complete("second") }
      } ~> check { entityAs[String] === "second" }
    }
    "collect rejections from both sub routes" in {
      Delete() ~> {
        get { completeOk } ~ put { completeOk }
      } ~> check { rejections === Seq(MethodRejection(GET), MethodRejection(PUT)) }
    }
    "clear rejections that have already been 'overcome' by previous directives" in {
      Put() ~> {
        put { parameter('yeah) { echoComplete } } ~
        get { completeOk }
      } ~> check { rejection === MissingQueryParamRejection("yeah") }
    }
  }

  "the jsonpWithParameter directive" should {
    val jsonResponse = HttpResponse(entity = HttpBody(`application/json`, "[1,2,3]"))
    "convert JSON responses to corresponding javascript responses according to the given JSONP parameter" in {
      Get("/?jsonp=someFunc") ~> {
        jsonpWithParameter("jsonp") {
          complete(jsonResponse)
        }
      } ~> check { body === HttpBody(`application/javascript`, "someFunc([1,2,3])") }
    }
    "not act on JSON responses if no jsonp parameter is present" in {
      Get() ~> {
        jsonpWithParameter("jsonp") {
          complete(jsonResponse)
        }
      } ~> check { response.entity === jsonResponse.entity }
    }
    "not act on non-JSON responses even if a jsonp parameter is present" in {
      Get("/?jsonp=someFunc") ~> {
        jsonpWithParameter("jsonp") {
          complete(HttpResponse(entity = HttpBody(`text/plain`, "[1,2,3]")))
        }
      } ~> check { body === HttpBody(`text/plain`, "[1,2,3]") }
    }
  }

  "the redirect directive" should {
    "produce proper 'Found' redirections" in {
      Get() ~> {
        redirect("/foo", Found)
      } ~> check {
        response === HttpResponse(
          status = 302,
          entity = HttpBody(`text/html`, "The requested resource temporarily resides under <a href=\"/foo\">this URI</a>."),
          headers = Location("/foo") :: Nil
        )
      }
    }
    "produce proper 'NotModified' redirections" in {
      Get() ~> {
        redirect("/foo", NotModified)
      } ~> check { response === HttpResponse(304, headers = Location("/foo") :: Nil) }
    }
  }

  "the clientIP directive" should {
    "extract from a X-Forwarded-For header" in {
      Get() ~> addHeaders(`X-Forwarded-For`("2.3.4.5"), RawHeader("x-real-ip", "1.2.3.4")) ~> {
        clientIP { echoComplete }
      } ~> check { entityAs[String] === "2.3.4.5" }
    }
    "extract from a X-Real-IP header" in {
      Get() ~> addHeaders(RawHeader("x-real-ip", "1.2.3.4"), `Remote-Address`("5.6.7.8")) ~> {
        clientIP { echoComplete }
      } ~> check { entityAs[String] === "5.6.7.8" }
    }
    "extract from a X-Real-IP header" in {
      Get() ~> addHeader(RawHeader("x-real-ip", "1.2.3.4")) ~> {
        clientIP { echoComplete }
      } ~> check { entityAs[String] === "1.2.3.4" }
    }
  }

  "the 'dynamic' directive" should {
    "cause its inner route to be revaluated for every request anew" in {
      var a = ""
      val staticRoute = get { a += "x"; complete(a) }
      val dynamicRoute = get { dynamic { a += "x"; complete(a) } }
      def expect(route: Route, s: String) = Get() ~> route ~> check { entityAs[String] === s }
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(dynamicRoute, "xx")
      expect(dynamicRoute, "xxx")
      expect(dynamicRoute, "xxxx")
    }
  }

  "The `rewriteUnmatchedPath` directive" should {
    "rewrite the unmatched path" in {
      Get("/abc") ~> {
        rewriteUnmatchedPath(_ + "/def") {
          path("abc/def") { completeOk }
        }
      } ~> check { response === Ok }
    }
  }
}