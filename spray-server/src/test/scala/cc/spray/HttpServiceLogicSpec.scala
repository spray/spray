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

import authentication.BasicUserContext
import typeconversion._
import http._
import HttpHeaders._
import HttpMethods._
import StatusCodes._
import MediaTypes._
import test.AbstractSprayTest
import utils.IllegalResponseException
import xml.NodeSeq
import encoding._

class HttpServiceLogicSpec extends AbstractSprayTest {
  
  implicit val userPassAuth = new UserPassAuthenticator[BasicUserContext] {
    def apply(userPass: Option[(String, String)]) = None
  }
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      testService(HttpRequest(GET, "/test")) {
        path("abc") { completeWith(Ok) }
      }.handled must beFalse
    }
    
    "leave only partially matched requests unhandled" in {
      "for routes on prefix paths" in {
        testService(HttpRequest(GET, "/test/more")) {
          path("test") { completeWith(Ok) }
        }.handled must beFalse
      }
      "for root path routes" in {
        testService(HttpRequest(GET, "/test")) {
          path("") { completeWith(Ok) }
        }.handled must beFalse
      }
    }
    
    "respond with the route response for completely matched requests" in {
      "for routes on non-root paths" in {
        testService(HttpRequest(GET, "/test")) {
          path("test") { completeWith(Ok) }
        }.response mustEqual Ok
      }
      "for routes on root paths" in {
        testService(HttpRequest(GET, "/")) {
          path("") { completeWith(Ok) }
        }.response mustEqual Ok
      }
    }
    
    "respond with the failure content on HTTP Failures" in {
      testService(HttpRequest(GET, "/")) {
        get { _.fail(BadRequest, "Some obscure error msg") }
      }.response mustEqual HttpResponse(BadRequest, "Some obscure error msg")
    }
    "respond with the response content even in failure cases, when the response has a content set" in {
      testService(HttpRequest(GET, "/")) {
        get { completeWith(HttpResponse(BadRequest, "Some content")) }
      }.response mustEqual HttpResponse(BadRequest, "Some content")
    }
    
    "throw an IllegalResponseException if the response contains a Content-Type header" in {
      testService(HttpRequest(GET, "/")) {
        respondWithHeader(`Content-Type`(`text/plain`)) { completeWith(Ok) }
      } must throwA[IllegalResponseException]
    }
    "throw an IllegalResponseException if the response contains a Content-Length header" in {
      testService(HttpRequest(GET, "/")) {
        respondWithHeader(`Content-Length`(42)) { completeWith(Ok) }
      } must throwA[IllegalResponseException]
    }
    
    // REJECTIONS

    "respond with Forbidden for requests resulting in an AuthorizationFailedRejection" in {
      testService(HttpRequest(headers = Authorization(BasicHttpCredentials("bob", "")) :: Nil)) {
        authenticate(httpBasic()) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(Forbidden, "The supplied authentication is either invalid " +
              "or not authorized to access this resource")
    }
    "respond with Unauthorized plus WWW-Authenticate header for AuthenticationRequiredRejections" in {
      testService(HttpRequest()) {
        authenticate(httpBasic()) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(Unauthorized, `WWW-Authenticate`(HttpChallenge("Basic", "Secured Resource")) :: Nil,
          "The resource requires authentication, which was not supplied with the request")
    }
    "respond with Forbidden for requests resulting in an AuthorizationFailedRejection" in {
      testService(HttpRequest()) {
        authorize(false) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(Forbidden, "The supplied authentication is either invalid or not authorized to access this resource")
    }
    "respond with BadRequest for requests resulting in a CorruptRequestEncodingRejection" in {
      testService(HttpRequest(headers = List(`Content-Encoding`(HttpEncodings.gzip)), content = Some(HttpContent(`text/plain`, "xyz")))) {
        decodeRequest(Gzip) { completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "The requests encoding is corrupt:\nNot in GZIP format")
    }
    "respond with BadRequest for requests resulting in a MalformedFormFieldRejection" in {
      testService(HttpRequest(POST, content = Some(FormData(Map("amount" -> "12.2")).toHttpContent))) {
        formField('amount.as[Int]) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "The form field 'amount' was malformed:\n" +
              "'12.2' is not a valid 32-bit integer value")
    }
    "respond with BadRequest for requests resulting in a MalformedQueryParamRejection" in {
      testService(HttpRequest(POST, "/?amount=xyz")) {
        parameters('amount.as[Int]) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "The query parameter 'amount' was malformed:\n" +
              "'xyz' is not a valid 32-bit integer value")
    }
    "respond with BadRequest for requests resulting in MalformedRequestContentRejections" in {
      testService(HttpRequest(POST, content = Some(HttpContent(`text/xml`, "<broken>xmlbroken>")))) {
        content(as[NodeSeq]) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "The request content was malformed:\n" +
              "XML document structures must start and end within the same entity.")
    }
    "respond with MethodNotAllowed for requests resulting in MethodRejections" in {
      testService(HttpRequest(POST, "/test")) {
        get { completeWith("yes") } ~
        put { completeWith("yes") }
      }.response mustEqual HttpResponse(MethodNotAllowed, "HTTP method not allowed, supported methods: GET, PUT")
    }    
    "respond with BadRequest for requests resulting in a MissingFormFieldRejection" in {
      testService(HttpRequest(POST)) {
        formFields('amount, 'orderId) { (_, _) => completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "Request is missing required form field 'amount'")
    }
    "respond with NotFound for requests resulting in a MissingQueryParamRejection" in {
      testService(HttpRequest(POST)) {
        parameters('amount, 'orderId) { (_, _) => completeWith(Ok) }
      }.response mustEqual HttpResponse(NotFound, "Request is missing required query parameter 'amount'")
    }
    "respond with BadRequest for requests resulting in RequestEntityExpectedRejection" in {
      testService(HttpRequest(POST)) {
        content(as[NodeSeq]) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "Request entity expected but not supplied")
    }
    "respond with NotAcceptable for requests resulting in UnacceptedResponseContentTypeRejection" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        get { completeWith("text text text") }
      }.response mustEqual HttpResponse(NotAcceptable, "Resource representation is only available " +
              "with these Content-Types:\ntext/plain")
    }
    "respond with NotAcceptable for requests resulting in UnacceptedResponseEncodingRejection" in {
      testService(HttpRequest(headers = List(`Accept-Encoding`(HttpEncodings.identity)))) {
        (encodeResponse(Gzip) | encodeResponse(Deflate)) { completeWith(Ok) }
      }.response mustEqual HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Encodings:\ngzip\ndeflate")
    }
    "respond with UnsupportedMediaType for requests resulting in UnsupportedRequestContentTypeRejection" in {
      testService(HttpRequest(POST, content = Some(HttpContent(`application/pdf`, "...PDF...")))) {
        content(as[NodeSeq]) { _ => completeWith(Ok) }
      }.response mustEqual HttpResponse(UnsupportedMediaType, "There was a problem with the requests Content-Type:\n" +
              "Expected 'text/xml' or 'text/html' or 'application/xhtml+xml'")
    }
    "respond with BadRequest for requests resulting in UnsupportedRequestContentTypeRejection" in {
      testService(HttpRequest(content = Some(HttpContent(`text/plain`, "Hello")))) {
        (decodeRequest(Gzip) | decodeRequest(Deflate)) { completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "The requests Content-Encoding must be one the following:\ngzip\ndeflate")
    }
    "respond with BadRequest for requests resulting in a ValidationRejection" in {
      testService(HttpRequest()) {
        validate(false, "Oh noo!") { completeWith(Ok) }
      }.response mustEqual HttpResponse(BadRequest, "Oh noo!")
    }
  }
  
}