/*
 * Copyright (C) 2011-2013 spray.io
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
import spray.routing.directives.RouteDirectives._

trait RejectionHandler extends RejectionHandler.PF

object RejectionHandler {
  type PF = PartialFunction[List[Rejection], Route]

  implicit def fromPF(pf: PF): RejectionHandler =
    new RejectionHandler {
      def isDefinedAt(rejections: List[Rejection]) = pf.isDefinedAt(rejections)
      def apply(rejections: List[Rejection]) = pf(rejections)
    }

  implicit val Default = fromPF {
    case Nil ⇒ complete(NotFound, "The requested resource could not be found.")

    case AuthenticationRequiredRejection(scheme, realm, params) :: _ ⇒
      complete(Unauthorized, `WWW-Authenticate`(HttpChallenge(scheme, realm, params)) :: Nil,
        "The resource requires authentication, which was not supplied with the request")

    case AuthenticationFailedRejection(realm) :: _ ⇒
      complete(Unauthorized, "The supplied authentication is invalid")

    case AuthorizationFailedRejection :: _ ⇒
      complete(Forbidden, "The supplied authentication is not authorized to access this resource")

    case CorruptRequestEncodingRejection(msg) :: _ ⇒
      complete(BadRequest, "The requests encoding is corrupt:\n" + msg)

    case MalformedFormFieldRejection(name, msg, _) :: _ ⇒
      complete(BadRequest, "The form field '" + name + "' was malformed:\n" + msg)

    case MalformedHeaderRejection(headerName, msg, _) :: _ ⇒
      complete(BadRequest, "The value of HTTP header '" + headerName + "' was malformed:\n" + msg)

    case MalformedQueryParamRejection(name, msg, _) :: _ ⇒
      complete(BadRequest, "The query parameter '" + name + "' was malformed:\n" + msg)

    case MalformedRequestContentRejection(msg, _) :: _ ⇒
      complete(BadRequest, "The request content was malformed:\n" + msg)

    case rejections @ (MethodRejection(_) :: _) ⇒
      // TODO: add Allow header (required by the spec)
      val methods = rejections.collect { case MethodRejection(method) ⇒ method }
      complete(MethodNotAllowed, "HTTP method not allowed, supported methods: " + methods.mkString(", "))

    case MissingCookieRejection(cookieName) :: _ ⇒
      complete(BadRequest, "Request is missing required cookie '" + cookieName + '\'')

    case MissingFormFieldRejection(fieldName) :: _ ⇒
      complete(BadRequest, "Request is missing required form field '" + fieldName + '\'')

    case MissingHeaderRejection(headerName) :: _ ⇒
      complete(BadRequest, "Request is missing required HTTP header '" + headerName + '\'')

    case MissingQueryParamRejection(paramName) :: _ ⇒
      complete(NotFound, "Request is missing required query parameter '" + paramName + '\'')

    case RequestEntityExpectedRejection :: _ ⇒
      complete(BadRequest, "Request entity expected but not supplied")

    case rejections @ (UnacceptedResponseContentTypeRejection(_) :: _) ⇒
      val supported = rejections.flatMap { case UnacceptedResponseContentTypeRejection(supported) ⇒ supported }
      complete(NotAcceptable, "Resource representation is only available with these Content-Types:\n" + supported.map(_.value).mkString("\n"))

    case rejections @ (UnacceptedResponseEncodingRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnacceptedResponseEncodingRejection(supported) ⇒ supported }
      complete(NotAcceptable, "Resource representation is only available with these Content-Encodings:\n" + supported.map(_.value).mkString("\n"))

    case rejections @ (UnsupportedRequestContentTypeRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnsupportedRequestContentTypeRejection(supported) ⇒ supported }
      complete(UnsupportedMediaType, "There was a problem with the requests Content-Type:\n" + supported.mkString(" or "))

    case rejections @ (UnsupportedRequestEncodingRejection(_) :: _) ⇒
      val supported = rejections.collect { case UnsupportedRequestEncodingRejection(supported) ⇒ supported }
      complete(BadRequest, "The requests Content-Encoding must be one the following:\n" + supported.map(_.value).mkString("\n"))

    case ValidationRejection(msg, _) :: _ ⇒
      complete(BadRequest, msg)
  }

  /**
   * Filters out all TransformationRejections from the given sequence and applies them (in order) to the
   * remaining rejections.
   */
  def applyTransformations(rejections: List[Rejection]): List[Rejection] = {
    val (transformations, rest) = rejections.partition(_.isInstanceOf[TransformationRejection])
    (rest.distinct /: transformations.asInstanceOf[Seq[TransformationRejection]]) {
      case (remaining, transformation) ⇒ transformation.transform(remaining)
    }
  }
}
