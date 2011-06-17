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
import MediaTypes._
import utils.{Logging, Rfc1123, IllegalResponseException}

/**
 * The logic part of the [[cc.spray.HttpService]]. Contains the code for [[cc.spray.RequestContext]] creation as well
 * as translation of [[cc.spray.Rejection]]s and Exceptions to [[cc.spray.http.HttpResponse]]s. 
 */
trait HttpServiceLogic extends ErrorHandling {
  this: Logging =>
  
  def setDateHeader: Boolean
  
  def route: Route
  
  def handle(request: HttpRequest) {
    val context = contextForRequest(request)
    try {
      route(context)
    } catch {
      case e: IllegalResponseException => throw e
      case e: Exception => context.complete(responseForException(request, e))
    }
  }
  
  protected def contextForRequest(request: HttpRequest): RequestContext = {
    val path = request.path
    RequestContext(request, responderForRequest(request), path)
  }
  
  protected def responderForRequest(request: HttpRequest): RoutingResult => Unit
  
  protected[spray] def responseFromRoutingResult(rr: RoutingResult): Option[HttpResponse] = rr match {
    case Respond(httpResponse) => Some(finalizeResponse(httpResponse)) 
    case Reject(rejections) => {
      val activeRejections = Rejections.applyCancellations(rejections)
      if (activeRejections.isEmpty) None else Some(finalizeResponse(responseForRejections(activeRejections.toList)))
    }
  }
  
  protected[spray] def responseForRejections(rejections: List[Rejection]): HttpResponse = {
    def handle[R <: Rejection :ClassManifest]: Option[HttpResponse] = {
      val erasure = classManifest.erasure
      rejections.filter(erasure.isInstance(_)) match {
        case Nil => None
        case filtered => Some(handleRejections(filtered))
      }
    }
    (handle[MethodRejection] getOrElse
    (handle[MissingQueryParamRejection] getOrElse
    (handle[MalformedQueryParamRejection] getOrElse
    (handle[AuthenticationRequiredRejection] getOrElse
    (handle[AuthorizationFailedRejection.type] getOrElse
    (handle[UnsupportedRequestContentTypeRejection] getOrElse
    (handle[RequestEntityExpectedRejection.type] getOrElse
    (handle[UnacceptedResponseContentTypeRejection] getOrElse
    (handle[MalformedRequestContentRejection] getOrElse
    (handle[ValidationRejection] getOrElse
    (handleCustomRejections(rejections))))))))))))
  }
  
  protected def handleRejections(rejections: List[Rejection]): HttpResponse = rejections match {
    case (_: MethodRejection) :: _ =>
      // TODO: add Allow header (required by the spec)
      val methods = rejections.collect { case MethodRejection(method) => (method) }
      HttpResponse(MethodNotAllowed, "HTTP method not allowed, supported methods: " + methods.mkString(", "))
    case MissingQueryParamRejection(paramName) :: _ =>
      HttpResponse(NotFound, "Request is missing required query parameter '" + paramName + '\'')
    case MalformedQueryParamRejection(msg, Some(name)) :: _ =>
      HttpResponse(BadRequest, "The query parameter '" + name + "' was malformed:\n" + msg)
    case MalformedQueryParamRejection(msg, None) :: _ =>
      HttpResponse(BadRequest, "One or more query parameters were illegal:\n" + msg)
    case AuthenticationRequiredRejection(scheme, realm, params) :: _ =>
      HttpResponse(Unauthorized, `WWW-Authenticate`(scheme, realm, params) :: Nil,
              "The resource requires authentication, which was not supplied with the request")
    case AuthorizationFailedRejection :: _ =>
      HttpResponse(Forbidden, "The supplied authentication is either invalid " +
              "or not authorized to access this resource")
    case UnsupportedRequestContentTypeRejection(supported) :: _ =>
      HttpResponse(UnsupportedMediaType, "The requests Content-Type must be one the following:\n" +
              supported.map(_.value).mkString("\n"))
    case RequestEntityExpectedRejection :: _ =>
      HttpResponse(BadRequest, "Request entity expected")
    case UnacceptedResponseContentTypeRejection(supported) :: _ =>
      HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Types:\n" +
              supported.map(_.value).mkString("\n"))
    case MalformedRequestContentRejection(msg) :: _ =>
        HttpResponse(BadRequest, "The request content was malformed:\n" + msg)
    case ValidationRejection(msg) :: _ => HttpResponse(BadRequest, msg)
    case _ => throw new IllegalStateException
  }
  
  protected def handleCustomRejections(rejections: List[Rejection]): HttpResponse = {
    HttpResponse(InternalServerError, "Unknown request rejection: " + rejections.head)
  }
  
  protected def finalizeResponse(unverifiedResponse: HttpResponse) = {
    val response = verified(unverifiedResponse)
    if (setDateHeader) {
      response.copy(headers = Date(Rfc1123.now) :: response.headers) 
    } else response
  }
  
  protected def verified(response: HttpResponse) = {
    response.headers.mapFind {
      case _: `Content-Type` => Some("HttpResponse must not include explicit 'Content-Type' header, " +
              "use the respective HttpContent member!")
      case _: `Content-Length` => Some("HttpResponse must not include explicit 'Content-Length' header, " +
              "this header will be set implicitly!")
      case _ => None
    } match {
        case Some(errorMsg) => throw new IllegalResponseException(errorMsg)
        case None => response
    } 
  }
  
}