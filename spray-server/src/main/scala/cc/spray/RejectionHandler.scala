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
import StatusCodes._
import HttpHeaders._

object RejectionHandler {

  lazy val Default: RejectionHandler = {
    case AuthenticationRequiredRejection(scheme, realm, params) :: _ =>
      HttpResponse(Unauthorized, `WWW-Authenticate`(HttpChallenge(scheme, realm, params)) :: Nil, "The resource requires authentication, which was not supplied with the request")
    case AuthenticationFailedRejection(realm) :: _ =>
      HttpResponse(Unauthorized, "The supplied authentication is invalid")
    case AuthorizationFailedRejection :: _ =>
      HttpResponse(Forbidden, "The supplied authentication is not authorized to access this resource")
    case CorruptRequestEncodingRejection(msg) :: _ =>
      HttpResponse(BadRequest, "The requests encoding is corrupt:\n" + msg)
    case MalformedFormFieldRejection(msg, name) :: _ =>
      HttpResponse(BadRequest, "The form field '" + name + "' was malformed:\n" + msg)
    case MalformedQueryParamRejection(msg, name) :: _ =>
      HttpResponse(BadRequest, "The query parameter '" + name + "' was malformed:\n" + msg)
    case MalformedRequestContentRejection(msg) :: _ =>
      HttpResponse(BadRequest, "The request content was malformed:\n" + msg)
    case rejections if rejections.head.isInstanceOf[MethodRejection] =>
      // TODO: add Allow header (required by the spec)
      val methods = rejections.collect { case MethodRejection(method) => method }
      HttpResponse(MethodNotAllowed, "HTTP method not allowed, supported methods: " + methods.mkString(", "))
    case MissingCookieRejection(cookieName) :: _ =>
      HttpResponse(BadRequest, "Request is missing required cookie '" + cookieName + '\'')
    case MissingFormFieldRejection(fieldName) :: _ =>
      HttpResponse(BadRequest, "Request is missing required form field '" + fieldName + '\'')
    case MissingHeaderRejection :: _ =>
      HttpResponse(BadRequest, "Request is missing a required HTTP header")
    case MissingQueryParamRejection(paramName) :: _ =>
      HttpResponse(NotFound, "Request is missing required query parameter '" + paramName + '\'')
    case RequestEntityExpectedRejection :: _ =>
      HttpResponse(BadRequest, "Request entity expected but not supplied")
    case rejections if rejections.head.isInstanceOf[UnacceptedResponseContentTypeRejection] =>
      val supported = rejections.flatMap { case UnacceptedResponseContentTypeRejection(supported) => supported }
      HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Types:\n" + supported.map(_.value).mkString("\n"))
    case rejections if rejections.head.isInstanceOf[UnacceptedResponseEncodingRejection] =>
      val supported = rejections.collect { case UnacceptedResponseEncodingRejection(supported) => supported }
      HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Encodings:\n" + supported.map(_.value).mkString("\n"))
    case rejections if rejections.head.isInstanceOf[UnsupportedRequestContentTypeRejection] =>
      val supported = rejections.collect { case UnsupportedRequestContentTypeRejection(supported) => supported }
      HttpResponse(UnsupportedMediaType, "There was a problem with the requests Content-Type:\n" + supported.mkString(" or "))
    case rejections if rejections.head.isInstanceOf[UnsupportedRequestEncodingRejection] =>
      val supported = rejections.collect { case UnsupportedRequestEncodingRejection(supported) => supported }
      HttpResponse(BadRequest, "The requests Content-Encoding must be one the following:\n" + supported.map(_.value).mkString("\n"))
    case ValidationRejection(msg) :: _ =>
      HttpResponse(BadRequest, msg)
  }

}