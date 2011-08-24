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
import utils.{Logging, IllegalResponseException}

/**
 * The logic part of the [[cc.spray.HttpService]]. Contains the code for [[cc.spray.RequestContext]] creation as well
 * as translation of [[cc.spray.Rejection]]s and Exceptions to [[cc.spray.http.HttpResponse]]s. 
 */
trait HttpServiceLogic extends ErrorHandling {
  this: Logging =>
  
  /**
   * The route of this HttpService
   */
  def route: Route

  /**
   * A custom handler function for rejections.
   * All rejections not handled by this PartialFunction will be responded to with the default logic.
   */
  def customRejectionHandler: PartialFunction[List[Rejection], HttpResponse]
  
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
    RequestContext(request, responderForRequest(request), initialUnmatchedPath(request))
  }

  protected def initialUnmatchedPath(request: HttpRequest) = {
    SpraySettings.RootPath match {
      case None => request.path
      case Some(rootPath) if (request.path.startsWith(rootPath)) => request.path.substring(rootPath.length)
      case Some(rootPath) => make(request.path) { path =>
        log.warn("Received request outside of configured root-path, request uri '%s', configured root path '%s'",
          path, rootPath)
      }
    }
  }
  
  protected def responderForRequest(request: HttpRequest): RoutingResult => Unit
  
  protected[spray] def responseFromRoutingResult(rr: RoutingResult): Option[HttpResponse] = rr match {
    case Respond(httpResponse) => Some(verify(httpResponse))
    case Reject(rejections) => {
      val activeRejections = Rejections.applyCancellations(rejections)
      if (activeRejections.isEmpty) None else Some(verify(responseForRejections(activeRejections.toList)))
    }
  }
  
  protected[spray] def responseForRejections(rejections: List[Rejection]): HttpResponse = {
    if (customRejectionHandler.isDefinedAt(rejections))
      customRejectionHandler(rejections)
    else rejections match {
      case AuthenticationRequiredRejection(scheme, realm, params) :: _ => HttpResponse(Unauthorized, `WWW-Authenticate`(HttpChallenge(scheme, realm, params)) :: Nil, "The resource requires authentication, which was not supplied with the request")
      case AuthenticationFailedRejection(realm) :: _ => HttpResponse(Forbidden, "The supplied authentication is either invalid or not authorized to access this resource")
      case AuthorizationFailedRejection :: _ => HttpResponse(Forbidden, "The supplied authentication is either invalid or not authorized to access this resource")
      case CorruptRequestEncodingRejection(msg) :: _ => HttpResponse(BadRequest, "The requests encoding is corrupt:\n" + msg)
      case MalformedQueryParamRejection(msg, Some(name)) :: _ => HttpResponse(BadRequest, "The query parameter '" + name + "' was malformed:\n" + msg)
      case MalformedQueryParamRejection(msg, None) :: _ => HttpResponse(BadRequest, "One or more query parameters were illegal:\n" + msg)
      case MalformedRequestContentRejection(msg) :: _ => HttpResponse(BadRequest, "The request content was malformed:\n" + msg)
      case (_: MethodRejection) :: _ =>
        // TODO: add Allow header (required by the spec)
        val methods = rejections.collect { case MethodRejection(method) => method }
        HttpResponse(MethodNotAllowed, "HTTP method not allowed, supported methods: " + methods.mkString(", "))
      case MissingQueryParamRejection(paramName) :: _ => HttpResponse(NotFound, "Request is missing required query parameter '" + paramName + '\'')
      case RequestEntityExpectedRejection :: _ => HttpResponse(BadRequest, "Request entity expected but not supplied")
      case (_: UnacceptedResponseContentTypeRejection) :: _ =>
        val supported = rejections.flatMap { case UnacceptedResponseContentTypeRejection(supported) => supported }
        HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Types:\n" + supported.map(_.value).mkString("\n"))
      case (_: UnacceptedResponseEncodingRejection) :: _ =>
        val supported = rejections.collect { case UnacceptedResponseEncodingRejection(supported) => supported }
        HttpResponse(NotAcceptable, "Resource representation is only available with these Content-Encodings:\n" + supported.mkString("\n"))
      case (_: UnsupportedRequestContentTypeRejection) :: _ =>
        val supported = rejections.flatMap { case UnsupportedRequestContentTypeRejection(supported) => supported }
        HttpResponse(UnsupportedMediaType, "The requests Content-Type must be one the following:\n" + supported.map(_.value).mkString("\n"))
      case (_: UnsupportedRequestEncodingRejection) :: _ =>
        val supported = rejections.collect { case UnsupportedRequestEncodingRejection(supported) => supported }
        HttpResponse(BadRequest, "The requests Content-Encoding must be one the following:\n" + supported.mkString("\n"))
      case ValidationRejection(msg) :: _ => HttpResponse(BadRequest, msg)
      case _ => HttpResponse(InternalServerError, "Unknown request rejection: " + rejections.head)
    }
  }
  
  protected def verify(response: HttpResponse) = {
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