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
import marshalling.{CantMarshal, MarshalWith}

/**
 * Immutable object encapsulating the context of an [[cc.spray.http.HttpRequest]]
 * as it flows through a ''spray'' Route structure.
 */
case class RequestContext(request: HttpRequest, responder: RoutingResult => Unit, unmatchedPath: String) {

  /**
   * Returns a copy of this context with the HttpRequest transformed by the given function.
   */
  def withRequestTransformed(f: HttpRequest => HttpRequest): RequestContext = {
    val newRequest = f(request)
    if (newRequest eq request) this else copy(request = newRequest)
  }

  /**
   * Returns a copy of this context with the the given response transformation function chained into the responder.
   */
  def withHttpResponseTransformed(f: HttpResponse => HttpResponse): RequestContext = {
    withRoutingResultTransformed {
      _ match {
        case Respond(response) => Respond(f(response))
        case x: Reject => x
      }
    }
  }
  
  /**
   * Returns a copy of this context with the the given RoutingResult transformation function chained into the responder.
   */
  def withRoutingResultTransformed(f: RoutingResult => RoutingResult): RequestContext = {
    withResponder { rr => responder(f(rr)) }
  }

  /**
   * Returns a copy of this context with the responder replaced by the given responder.
   */
  def withResponder(newResponder: RoutingResult => Unit) = copy(responder = newResponder)

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*) { reject(Set(rejections: _*)) }
  
  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Set[Rejection]) { responder(Reject(rejections)) }

  /**
   * Completes the request with status "200 Ok" and response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[A :Marshaller](obj: A) {
    marshaller.apply(request.acceptableContentType) match {
      case MarshalWith(converter) => complete(converter(obj))
      case CantMarshal(onlyTo) => reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  }

  /**
   * Completes the request with status "200 Ok" and the given response content.
   */
  def complete(content: HttpContent) { complete(HttpResponse(content = Some(content))) }

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse) { responder(Respond(response)) }

  /**
   * Returns a copy of this context that cancels all rejections of type R with
   * a [[cc.spray.RejectionRejection]]. 
   */
  def cancelRejections[R <: Rejection :Manifest]: RequestContext = {
    val erasure = manifest.erasure
    cancelRejections(erasure.isInstance(_))
  }
  
  /**
   * Returns a copy of this context that cancels all rejections matching the given predicate with
   * a [[cc.spray.RejectionRejection]].
   */
  def cancelRejections(reject: Rejection => Boolean): RequestContext = {
    withRoutingResultTransformed {
      _ match {
        case x: Respond => x
        case Reject(rejections) => Reject(rejections + RejectionRejection(reject))
      }
    }
  }
  
  /**
   * Completes the request with the given [[cc.spray.http.HttpFailure]].
   */
  def fail(failure: HttpFailure, reason: String = "") {
    fail(HttpStatus(failure, reason))
  }
  
  /**
   * Completes the request with the given [[cc.spray.http.HttpStatus]].
   */
  def fail(failure: HttpStatus) {
    complete(HttpResponse(failure))
  }

  /**
   * Completes the request with a redirection response to the given URI.
   */
  def redirect(uri: String, redirectionType: Redirection = Found) {
    complete(
      HttpResponse(
        status = redirectionType,
        headers = Location(uri) :: Nil,
        content = Some(HttpContent(`text/html`,
          "The requested resource temporarily resides under this <a href=\"" + uri + "\">URI</a>."
        ))
      )
    )
  }
}

object RequestContext {
  def apply(request: HttpRequest, responder: RoutingResult => Unit = { _ => }): RequestContext = {
    apply(request, responder, request.path)
  }
}