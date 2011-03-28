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

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MediaTypes._
import test.SprayTest
import xml.NodeSeq

class HttpServiceLogicSpec extends Specification with SprayTest with ServiceBuilder {
  
  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      testService(HttpRequest(GET, "/test")) {
        path("abc") { completeOk }
      }.handled must beFalse
    }
    "leave only partially matched requests unhandled" in {
      "for routes on prefix paths" in {
        testService(HttpRequest(GET, "/test/more")) {
          path("test") { completeOk }
        }.handled must beFalse
      }
      "for root path routes" in {
        testService(HttpRequest(GET, "/test")) {
          path("") { completeOk }
        }.handled must beFalse
      }
    }
    "respond with the route response for completely matched requests" in {
      "for routes on non-root paths" in {
        testService(HttpRequest(GET, "/test")) {
          path("test") { completeOk }
        }.response mustEqual Ok
      }
      "for routes on root paths" in {
        testService(HttpRequest(GET, "/")) {
          path("") { completeOk }
        }.response mustEqual Ok
      }
    }
    "respond with MethodNotAllowed for fully-matched requests resulting in MethodRejections" in {
      testService(HttpRequest(POST, "/test")) {
        path("test") {
          get { _.complete("yes") } ~
          put { _.complete("yes") }
        }
      }.response mustEqual failure(MethodNotAllowed, "HTTP method not allowed, supported methods: GET, PUT")
    }    
    "respond with NotFound for fully-matched requests resulting in MissingQueryParamRejections" in {
      testService(HttpRequest(POST, "/")) {
        path("") {
          parameters('amount, 'orderId) { (_, _) => completeOk }
        }
      }.response mustEqual failure(NotFound, "Request is missing the following required query parameters: " +
              "orderId, amount")
    }
    "respond with UnsupportedMediaType for fully-matched requests resulting in UnsupportedRequestContentTypeRejection" in {
      testService(HttpRequest(POST, "/", content = Some(HttpContent(`application/pdf`, "...PDF...")))) {
        path("") {
          contentAs[NodeSeq] { _ => completeOk }
        }
      }.response mustEqual failure(UnsupportedMediaType, "The requests content-type must be one the following:\n" +
              "text/xml\ntext/html\napplication/xhtml+xml")
    }
    "respond with BadRequest for fully-matched requests resulting in RequestEntityExpectedRejection" in {
      testService(HttpRequest(POST, "/")) {
        path("") {
          contentAs[NodeSeq] { _ => completeOk }
        }
      }.response mustEqual failure(BadRequest, "Request entity expected")
    }
    "respond with NotAcceptable for fully-matched requests resulting in UnacceptedResponseContentTypeRejection" in {
      testService(HttpRequest(GET, "/", headers = List(`Accept`(`text/css`)))) {
        path("") {
          get { _.complete("text text text") }
        }
      }.response mustEqual failure(NotAcceptable, "Resource representation is only available with these content-types:\n" +
              "text/plain; charset=ISO-8859-1")
    }
    "respond with BadRequest for fully-matched requests resulting in MalformedRequestContentRejections" in {
      testService(HttpRequest(POST, "/", content = Some(HttpContent(`text/xml`, "<broken>xmlbroken>")))) {
        path("") {
          contentAs[NodeSeq] { _ => completeOk }
        }
      }.response mustEqual failure(BadRequest, "The request content was malformed:\n" +
              "XML document structures must start and end within the same entity.")
    }
  }
  
}