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
package builders

import org.specs.Specification
import http._
import HttpStatusCodes._
import HttpHeaders._
import HttpMethods._
import MediaTypes._
import test.SprayTest

class MiscBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  
  "respondWithStatus" should {
    "set the given status on successful responses" in {
      test(HttpRequest()) { 
        respondWithStatus(Created) { completeOk }
      }.response mustEqual HttpResponse(Created) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithStatus(Created) { _.reject() }
      }.rejections mustEqual Set() 
    }
  }
  
  "respondWithHeader" should {
    "add the given headers to successful responses" in {
      test(HttpRequest()) { 
        respondWithHeader(CustomHeader("custom", "custom")) { completeOk }
      }.response mustEqual HttpResponse(headers = CustomHeader("custom", "custom") :: Nil) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithHeader(CustomHeader("custom", "custom")) { _.reject() }
      }.rejections mustEqual Set() 
    }
  }
  
  "respondWithContentType" should {
    "set the content type of successful responses" in {
      test(HttpRequest()) { 
        respondWithContentType(`application/json`) { _.complete("plaintext") }
      }.response.content mustEqual Some(HttpContent(`application/json`, "plaintext")) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondWithContentType(`application/json`) { _.reject() }
      }.rejections mustEqual Set() 
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("second")    
    }
    "collect rejections from both sub routes" in {
      test(HttpRequest(DELETE)) {
        get { { _ => fail("Should not run") } } ~ put { { _ => fail("Should not run") } }
      }.rejections mustEqual Set(MethodRejection(GET), MethodRejection(PUT))
    }
    "clear rejections that have already 'overcome' by previous directives" in {
      test(HttpRequest(PUT)) {
        put { contentAs[String] { s => _.complete(s) }} ~
        get { completeOk }
      }.rejections mustEqual Set(RequestEntityExpectedRejection)
    }
  }
  
}