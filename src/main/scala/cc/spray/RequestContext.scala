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
import marshalling.{CantMarshal, MarshalWith}

case class RequestContext(request: HttpRequest, responder: RoutingResult => Unit, unmatchedPath: String) {
  
  def withRequestTransformed(f: HttpRequest => HttpRequest): RequestContext = {
    copy(request = f(request))
  }

  def withHttpResponseTransformed(f: HttpResponse => HttpResponse): RequestContext = {
    withRoutingResultTransformed {
      _ match {
        case Respond(response) => Respond(f(response))
        case x: Reject => x
      }
    }
  }
  
  def withRoutingResultTransformed(f: RoutingResult => RoutingResult): RequestContext = {
    withResponder { rr => responder(f(rr)) }
  }

  def withResponder(newResponder: RoutingResult => Unit) = copy(responder = newResponder)

  def reject(rejections: Rejection*) { reject(Set(rejections: _*)) }
  
  def reject(rejections: Set[Rejection]) { responder(Reject(rejections)) }

  def complete[A :Marshaller](obj: A) {
    marshaller.apply(request.isContentTypeAccepted(_)) match {
      case MarshalWith(converter) => complete(converter(obj))
      case CantMarshal(onlyTo) => reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  }

  def complete(content: HttpContent) { complete(HttpResponse(content = Some(content))) }

  def complete(response: HttpResponse) { responder(Respond(response)) }
  
  // can be cached
  def fail(failure: HttpFailure, reason: String = "") {
    fail(HttpStatus(failure, reason))
  }
  
  def fail(failure: HttpStatus) {
    responder(Respond(HttpResponse(failure)))
  }
}

object RequestContext {
  def apply(request: HttpRequest, responder: RoutingResult => Unit = { _ => }): RequestContext = {
    apply(request, responder, request.path)
  }
}