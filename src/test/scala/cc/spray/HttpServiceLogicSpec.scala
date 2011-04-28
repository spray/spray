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
    "respond with the failure content on HTTP Failures" in {
      testService(HttpRequest(GET, "/")) {
          get { _.fail(BadRequest, "Some obscure error msg") }
        }.response mustEqual failure (BadRequest, "Some obscure error msg")
    }
    "respond with MethodNotAllowed for requests resulting in MethodRejections" in {
      testService(HttpRequest(POST, "/test")) {
        get { _.complete("yes") } ~
        put { _.complete("yes") }
      }.response mustEqual failure(MethodNotAllowed, "HTTP method not allowed, supported methods: GET, PUT")
    }    
    "respond with NotFound for requests resulting in a MissingQueryParamRejection" in {
      testService(HttpRequest(POST)) {
        parameters('amount, 'orderId) { (_, _) => completeOk }
      }.response mustEqual failure(NotFound, "Request is missing required query parameter 'amount'")
    }
    "respond with BadRequest for requests resulting in a MalformedQueryParamRejection" in {
      testService(HttpRequest(POST, "/?amount=xyz")) {
        parameters('amount.as[Int]) { amount => completeOk }
      }.response mustEqual failure(BadRequest, "The query parameter 'amount' was malformed:\n" +
              "'xyz' is not a valid 32-bit integer value")
    }
    "respond with UnsupportedMediaType for requests resulting in UnsupportedRequestContentTypeRejection" in {
      testService(HttpRequest(POST, content = Some(HttpContent(`application/pdf`, "...PDF...")))) {
        contentAs[NodeSeq] { _ => completeOk }
      }.response mustEqual failure(UnsupportedMediaType, "The requests Content-Type must be one the following:\n" +
              "text/xml\ntext/html\napplication/xhtml+xml")
    }
    "respond with BadRequest for requests resulting in RequestEntityExpectedRejection" in {
      testService(HttpRequest(POST)) {
        contentAs[NodeSeq] { _ => completeOk }
      }.response mustEqual failure(BadRequest, "Request entity expected")
    }
    "respond with NotAcceptable for requests resulting in UnacceptedResponseContentTypeRejection" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        get { _.complete("text text text") }
      }.response mustEqual failure(NotAcceptable, "Resource representation is only available with these Content-Types:\n" +
              "text/plain")
    }
    "respond with BadRequest for requests resulting in MalformedRequestContentRejections" in {
      testService(HttpRequest(POST, content = Some(HttpContent(`text/xml`, "<broken>xmlbroken>")))) {
        contentAs[NodeSeq] { _ => completeOk }
      }.response mustEqual failure(BadRequest, "The request content was malformed:\n" +
              "XML document structures must start and end within the same entity.")
    }
  }
  
}