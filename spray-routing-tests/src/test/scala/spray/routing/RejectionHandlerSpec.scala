/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import scala.xml.NodeSeq
import spray.routing.authentication.BasicAuth
import spray.httpx.encoding._
import spray.http._
import HttpHeaders._
import StatusCodes._
import MediaTypes._
import spray.httpx.unmarshalling.Unmarshaller

class RejectionHandlerSpec extends RoutingSpec {

  val wrap = handleRejections(RejectionHandler.Default)

  "The default RejectionHandler" should {
    "respond with Forbidden for requests resulting in an AuthenticationFailedRejection" in {
      Get() ~> Authorization(BasicHttpCredentials("bob", "")) ~> wrap {
        authenticate(BasicAuth()) { _ ⇒ completeOk }
      } ~> check {
        status === Unauthorized
        responseAs[String] === "The supplied authentication is invalid"
      }
    }
    "respond with Unauthorized plus WWW-Authenticate header for AuthenticationRequiredRejections" in {
      Get() ~> wrap {
        authenticate(BasicAuth()) { _ ⇒ completeOk }
      } ~> check {
        status === Unauthorized
        headers === `WWW-Authenticate`(HttpChallenge("Basic", "Secured Resource")) :: Nil
        responseAs[String] === "The resource requires authentication, which was not supplied with the request"
      }
    }
    "respond with Forbidden for requests resulting in an AuthorizationFailedRejection" in {
      Get() ~> wrap {
        authorize(false) { completeOk }
      } ~> check {
        status === Forbidden
        responseAs[String] === "The supplied authentication is not authorized to access this resource"
      }
    }
    "respond with BadRequest for requests resulting in a CorruptRequestEncodingRejection" in {
      Get("/", "xyz") ~> `Content-Encoding`(HttpEncodings.gzip) ~> wrap {
        decodeRequest(Gzip) { completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "The requests encoding is corrupt:\nNot in GZIP format"
      }
    }
    "respond with BadRequest for requests resulting in a MalformedFormFieldRejection" in {
      Post("/", FormData(Map("amount" -> "12.2"))) ~> wrap {
        formField('amount.as[Int]) { _ ⇒ completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "The form field 'amount' was malformed:\n'12.2' is not a valid 32-bit integer value"
      }
    }
    "respond with BadRequest for requests resulting in a MalformedQueryParamRejection" in {
      Post("/?amount=xyz") ~> wrap {
        parameters('amount.as[Int]) { _ ⇒ completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "The query parameter 'amount' was malformed:\n" +
          "'xyz' is not a valid 32-bit integer value"
      }
    }
    "respond with BadRequest for requests resulting in MalformedRequestContentRejections" in {
      Post("/", HttpEntity(`text/xml`, "<broken>xmlbroken>")) ~> wrap {
        entity(as[NodeSeq]) { _ ⇒ completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] must startWith("The request content was malformed:")
      }
    }
    "respond with MethodNotAllowed for requests resulting in MethodRejections" in {
      import HttpMethods._
      Post("/", "/test") ~> wrap {
        get { complete("yes") } ~
          put { complete("yes") }
      } ~> check {
        status === MethodNotAllowed
        headers === Allow(GET, PUT) :: Nil
        responseAs[String] === "HTTP method not allowed, supported methods: GET, PUT"
      }
    }
    "respond with BadRequest for requests resulting in SchemeRejections" in {
      Get("http://example.com/hello") ~> wrap {
        scheme("https") { complete("yes") }
      } ~> check {
        status === BadRequest
        responseAs[String] === "Uri scheme not allowed, supported schemes: https"
      }
    }
    "respond with BadRequest for requests resulting in a MissingFormFieldRejection" in {
      Get() ~> wrap {
        formFields('amount, 'orderId) { (_, _) ⇒ completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "Request is missing required form field 'amount'"
      }
    }
    "respond with NotFound for requests resulting in a MissingQueryParamRejection" in {
      Get() ~> wrap {
        parameters('amount, 'orderId) { (_, _) ⇒ completeOk }
      } ~> check {
        status === NotFound
        responseAs[String] === "Request is missing required query parameter 'amount'"
      }
    }
    "respond with BadRequest for requests resulting in RequestEntityExpectedRejection" in {
      implicit val x = Unmarshaller.forNonEmpty[String]
      Post() ~> wrap {
        entity(as[String]) { _ ⇒ completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "Request entity expected but not supplied"
      }
    }
    "respond with NotAcceptable for requests resulting in UnacceptedResponseContentTypeRejection" in {
      Get() ~> `Accept`(`text/css`) ~> `Accept-Encoding`(HttpEncodings.identity) ~> {
        wrap { complete("text text text") ~ encodeResponse(Gzip)(complete("test")) }
      } ~> check {
        status === NotAcceptable
        responseAs[String] === "Resource representation is only available " +
          "with these Content-Types:\ntext/plain; charset=UTF-8\ntext/plain"
      }
    }
    "respond with NotAcceptable for requests resulting in UnacceptedResponseEncodingRejection" in {
      Get() ~> `Accept-Encoding`(HttpEncodings.identity) ~> wrap {
        (encodeResponse(Gzip) | encodeResponse(Deflate)) { completeOk }
      } ~> check {
        status === NotAcceptable
        responseAs[String] === "Resource representation is only available with these Content-Encodings:\ngzip\ndeflate"
      }
    }
    "respond with UnsupportedMediaType for requests resulting in UnsupportedRequestContentTypeRejection" in {
      Post("/", HttpEntity(`application/pdf`, "...PDF...")) ~> wrap {
        entity(as[NodeSeq]) { _ ⇒ completeOk }
      } ~> check {
        status === UnsupportedMediaType
        responseAs[String] === "There was a problem with the requests Content-Type:\n" +
          "Expected 'text/xml' or 'application/xml' or 'text/html' or 'application/xhtml+xml'"
      }
    }
    "respond with BadRequest for requests resulting in UnsupportedRequestContentTypeRejection" in {
      Post("/", "Hello") ~> wrap {
        (decodeRequest(Gzip) | decodeRequest(Deflate)) { completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "The requests Content-Encoding must be one the following:\ngzip\ndeflate"
      }
    }
    "respond with BadRequest for requests resulting in a ValidationRejection" in {
      Get() ~> wrap {
        validate(false, "Oh noo!") { completeOk }
      } ~> check {
        status === BadRequest
        responseAs[String] === "Oh noo!"
      }
    }
  }

}
