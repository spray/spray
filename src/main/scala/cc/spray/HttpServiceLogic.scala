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
import HttpHeaders._
import MediaTypes._
import utils.IllegalResponseException

/**
 * The logic part of the [[cc.spray.HttpService]]. Contains the code for [[cc.spray.RequestContext]] creation as well
 * as translation of [[cc.spray.Rejection]]s and Exceptions to [[cc.spray.http.HttpResponse]]s. 
 */
trait HttpServiceLogic extends ErrorHandling {
  
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
    case Respond(httpResponse) => Some(finalizeResponse(httpResponse)) 
    case Reject(rejections) => {
      val activeRejections = Rejections.applyCancellations(rejections)
      if (activeRejections.isEmpty) None else responseForRejections(activeRejections)
    }
  }
  
  protected[spray] def responseForRejections(rejections: Set[Rejection]): Option[HttpResponse] = {
    val r = rejections.toList
    handleMethodRejections(r) orElse
    handleMissingQueryParamRejections(r) orElse
    handleMalformedQueryParamRejections(r) orElse
    handleUnsupportedRequestContentTypeRejections(r) orElse
    handleRequestEntityExpectedRejection(r) orElse
    handleUnacceptedResponseContentTypeRejection(r) orElse
    handleMalformedRequestContentRejection(r) orElse
    handleCustomRejections(r) 
  }
  
  protected def handleMethodRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MethodRejection(method) => method }) match {
      case Nil => None
      case methods => {
        // TODO: add Allow header (required by the spec)
        Some(HttpResponse(HttpStatus(MethodNotAllowed, "HTTP method not allowed, supported methods: " +
                methods.mkString(", "))))
      }
    } 
  }
  
  protected def handleMissingQueryParamRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MissingQueryParamRejection(p) => p }) match {
      case Nil => None
      case paramName :: _ => {
        Some(HttpResponse(HttpStatus(NotFound, "Request is missing required query parameter '" + paramName + '\'')))
      }
    } 
  }
  
  protected def handleMalformedQueryParamRejections(rejections: List[Rejection]): Option[HttpResponse] = {
    (rejections.collect { case MalformedQueryParamRejection(name, msg) => (name, msg) }) match {
      case Nil => None
      case (name, msg) :: _ => {
        Some(HttpResponse(HttpStatus(BadRequest, "The query parameter '" + name + "' was malformed:\n" + msg)))
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
  
  protected def finalizeResponse(response: HttpResponse) = {
    response.headers.foreach {
      case _: `Content-Type` => throw new IllegalResponseException(
        "HttpResponse must not include explicit 'Content-Type' header, use the respective HttpContent member!"
      )
      case _: `Content-Length` => throw new IllegalResponseException(
        "HttpResponse must not include explicit 'Content-Length' header, this header will be set implicitly!"
      ) 
    }
    if (response.isSuccess) response 
    else response.withContentTransformed(content => HttpContent(`text/plain`, response.status.reason))
  }

}