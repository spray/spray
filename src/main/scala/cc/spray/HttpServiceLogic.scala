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
import HttpStatusCodes._

/**
 * The logic part of the [[cc.spray.HttpService]]. Contains the code for [[cc.spray.RequestContext]] creation as well
 * as translation of [[cc.spray.Rejection]]s and Exceptions to [[cc.spray.http.HttpResponse]]s. 
 */
trait HttpServiceLogic {
  
  def route: Route
  
  def handle(request: HttpRequest) {
    val context = contextForRequest(request)
    try {
      route(context)
    } catch {
      case e: Exception => context.complete(responseForException(request, e))
    }
  }
  
  protected[spray] def contextForRequest(request: HttpRequest): RequestContext = {
    RequestContext(request, responderForRequest(request))
  }
  
  protected[spray] def responderForRequest(request: HttpRequest): RoutingResult => Unit
  
  protected[spray] def responseFromRoutingResult(rr: RoutingResult): Option[HttpResponse] = rr match {
    case Respond(httpResponse) => Some(httpResponse) 
    case Reject(rejections) => if (rejections.isEmpty) None else responseForRejections(rejections.toSet)
  }
  
  protected[spray] def responseForRejections(rejections: Set[Rejection]): Option[HttpResponse] = {
    val r = rejections.toList
    handleMethodRejections(r) orElse
    handleMissingQueryParamRejections(r) orElse
    handleUnsupportedRequestContentTypeRejections(r) orElse
    handleRequestEntityExpectedRejection(r) orElse
    handleUnacceptedResponseContentTypeRejection(r) orElse
    handleMalformedRequestContentRejection(r) orElse
    handleCustomRejections(r) 
  }
  
  protected def handleMethodRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MethodRejection(method) => method }) match {
      case Nil => None
      case methodRejections => {
        // TODO: add Allow header (required by the spec)
        Some(HttpResponse(HttpStatus(MethodNotAllowed, "HTTP method not allowed, supported methods: " +
                methodRejections.mkString(", "))))
      }
    } 
  }
  
  protected def handleMissingQueryParamRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MissingQueryParamRejection(p) => p }) match {
      case Nil => None
      case missingQueryParamRejection => {
        Some(HttpResponse(HttpStatus(NotFound, "Request is missing the following required query parameters: " +
            missingQueryParamRejection.mkString(", "))))
      }
    } 
  }
  
  protected def handleUnsupportedRequestContentTypeRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case UnsupportedRequestContentTypeRejection(supported) => supported }) match {
      case Nil => None
      case supported :: _ => {
        Some(HttpResponse(HttpStatus(UnsupportedMediaType,
          "The requests content-type must be one the following:\n" + supported.map(_.value).mkString("\n"))))
      }
    } 
  }
  
  protected def handleRequestEntityExpectedRejection(rejections: List[Rejection]): Option[HttpResponse] = {
    if (rejections.contains(RequestEntityExpectedRejection)) {
      Some(HttpResponse(HttpStatus(BadRequest, "Request entity expected")))
    } else None
  }
  
  protected def handleUnacceptedResponseContentTypeRejection(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case UnacceptedResponseContentTypeRejection(supported) => supported }) match {
      case Nil => None
      case supported :: _ => {
        Some(HttpResponse(HttpStatus(NotAcceptable, "Resource representation is only available with these " +
                  "content-types:\n" + supported.map(_.value).mkString("\n"))))
      }
    } 
  }
  
  protected def handleMalformedRequestContentRejection(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MalformedRequestContentRejection(msg) => msg }) match {
      case Nil => None
      case msg :: _ => {
        Some(HttpResponse(HttpStatus(BadRequest, "The request content was malformed:\n" + msg)))
      }
    } 
  }
  
  protected def handleCustomRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    Some(HttpResponse(HttpStatus(InternalServerError, "Unknown request rejection: " + rejections.head)))
  }
  
  protected[spray] def responseForException(request: HttpRequest, e: Exception): HttpResponse = e match {
    case e: HttpException => HttpResponse(e.status)
    case e: Exception => HttpResponse(HttpStatus(InternalServerError, e.getMessage))
  } 
}