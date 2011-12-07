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
import StatusCodes._
import HttpHeaders._
import HttpMethods._
import MediaTypes._
import test.AbstractSprayTest

class MiscDirectivesSpec extends AbstractSprayTest {

  val reject: Route = _.reject()

  "respondWithStatus" should {
    "set the given status on successful responses" in {
      test(HttpRequest()) { 
        respondWithStatus(Created) { completeWith(Ok) }
      }.response mustEqual HttpResponse(Created) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithStatus(Created) { reject }
      }.rejections mustEqual Set() 
    }
  }
  
  "respondWithHeader" should {
    "add the given headers to successful responses" in {
      test(HttpRequest()) { 
        respondWithHeader(CustomHeader("custom", "custom")) { completeWith(Ok) }
      }.response mustEqual HttpResponse(OK, CustomHeader("custom", "custom") :: Nil) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithHeader(CustomHeader("custom", "custom")) { reject }
      }.rejections mustEqual Set() 
    }
  }
  
  "respondWithContentType" should {
    "set the content type of successful responses" in {
      test(HttpRequest()) { 
        respondWithContentType(`application/json`) { completeWith("plaintext") }
      }.response.content mustEqual Some(HttpContent(`application/json`, "plaintext")) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithContentType(`application/json`) { reject }
      }.rejections mustEqual Set() 
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { completeWith("first") } ~ get { completeWith("second") }
      }.response.content.as[String] mustEqual Right("first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { completeWith("first") } ~ get { completeWith("second") }
      }.response.content.as[String] mustEqual Right("second")    
    }
    "collect rejections from both sub routes" in {
      test(HttpRequest(DELETE)) {
        get { completeWith(Ok) } ~ put { completeWith(Ok) }
      }.rejections mustEqual Set(MethodRejection(GET), MethodRejection(PUT))
    }
    "clear rejections that have already been 'overcome' by previous directives" in {
      test(HttpRequest(PUT)) {
        put { content(as[String]) { echoComplete }} ~
        get { completeWith(Ok) }
      }.rejections mustEqual Set(RequestEntityExpectedRejection)
    }
  }

  "the jsonpWithParameter directive" should {
    "convert JSON responses to corresponding javascript respones according to the given JSONP parameter" in {
      test(HttpRequest(uri = "/?jsonp=someFunc")) {
        jsonpWithParameter("jsonp") {
          respondWithContentType(`application/json`) {
            completeWith("[1,2,3]")
          }
        }
      }.response.content.get mustEqual HttpContent(ContentType(`application/javascript`), "someFunc([1,2,3])")
    }
    "not act on JSON responses if no jsonp parameter is present" in {
      test(HttpRequest(uri = "/")) {
        jsonpWithParameter("jsonp") {
          respondWithContentType(`application/json`) {
            completeWith("[1,2,3]")
          }
        }
      }.response.content.get mustEqual HttpContent(ContentType(`application/json`), "[1,2,3]")
    }
    "not act on non-JSON responses even if a jsonp parameter is present" in {
      test(HttpRequest(uri = "/?jsonp=someFunc")) {
        jsonpWithParameter("jsonp") {
          respondWithContentType(`text/plain`) {
            completeWith("[1,2,3]")
          }
        }
      }.response.content.get mustEqual HttpContent(ContentType(`text/plain`), "[1,2,3]")
    }
  }
  
}