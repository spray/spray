/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing

import collection.GenTraversableOnce
import akka.dispatch.Future
import akka.actor.{Status, ActorRef}
import akka.spray.UnregisteredActorRef
import cc.spray.httpx.marshalling.{MarshallingContext, Marshaller}
import cc.spray.util._
import cc.spray.http._
import StatusCodes._
import HttpHeaders._
import MediaTypes._


/**
 * Immutable object encapsulating the context of an [[cc.spray.http.HttpRequest]]
 * as it flows through a ''spray'' Route structure.
 */
case class RequestContext(
  request: HttpRequest,
  handler: ActorRef,
  unmatchedPath: String = ""
) {

  /**
   * Returns a copy of this context with the HttpRequest transformed by the given function.
   */
  def mapRequest(f: HttpRequest => HttpRequest): RequestContext = {
    val transformed = f(request)
    if (transformed eq request) this else copy(request = transformed)
  }

  /**
   * Returns a copy of this context with the handler transformed by the given function.
   */
  def mapHandler(f: ActorRef => ActorRef) = {
    val transformed = f(handler)
    if (transformed eq handler) this else copy(handler = transformed)
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def mapRouteResponse(f: Any => Any) = mapHandler { previousHandler =>
    new UnregisteredActorRef(handler) {
      def handle(message: Any, sender: ActorRef) {
        previousHandler.tell(f(message), sender)
      }
    }
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def flatMapRouteResponse(f: Any => Seq[Any]) = mapHandler { previousHandler =>
    new UnregisteredActorRef(handler) {
      def handle(message: Any, sender: ActorRef) {
        f(message).foreach(previousHandler.tell(_, sender))
      }
    }
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def mapRouteResponsePF(f: PartialFunction[Any, Any]) = mapRouteResponse { message =>
    if (f.isDefinedAt(message)) f(message) else message
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def flatMapRouteResponsePF(f: PartialFunction[Any, Seq[Any]]) = flatMapRouteResponse { message =>
    if (f.isDefinedAt(message)) f(message) else message :: Nil
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def mapHttpResponse(f: HttpResponse => HttpResponse) = mapRouteResponse {
    case x: HttpResponse => f(x)
    case ChunkedResponseStart(x) => ChunkedResponseStart(f(x))
    case x => x
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def mapHttpResponseEntity(f: HttpEntity => HttpEntity) =
    mapHttpResponse(_.mapEntity(f))

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def mapHttpResponseHeaders(f: List[HttpHeader] => List[HttpHeader]) =
    mapHttpResponse(_.mapHeaders(f))

  /**
   * Returns a copy of this context with the given rejection transformation function chained into the response chain.
   */
  def mapRejections(f: Seq[Rejection] => Seq[Rejection]) = mapRouteResponse {
    case Rejected(rejections) => Rejected(f(rejections))
    case x => x
  }

  /**
   * Returns a copy of this context with the given function handling a part of the response space.
   */
  def withRouteResponseHandling(f: PartialFunction[Any, Unit]) = mapHandler { previousHandler =>
    new UnregisteredActorRef(handler) {
      def handle(message: Any, sender: ActorRef) {
        if (f.isDefinedAt(message)) f(message) else previousHandler.tell(message, sender)
      }
    }
  }

  /**
   * Returns a copy of this context with the given rejection handling function chained into the response chain.
   */
  def withRejectionHandling(f: Seq[Rejection] => Unit) = mapHandler { previousHandler =>
    new UnregisteredActorRef(handler) {
      def handle(message: Any, sender: ActorRef) {
        message match {
          case Rejected(rejections) => f(rejections)
          case x => previousHandler.tell(x, sender)
        }
      }
    }
  }

  /**
   * Returns a copy of this context that automatically sets the sender of all messages to its handler to the given
   * one, if no explicit sender is passed along from upstream.
   */
  def withDefaultSender(defaultSender: ActorRef) = copy(
    handler = new UnregisteredActorRef(handler) {
      def handle(message: Any, sender: ActorRef) {
        handler.tell(message, if (sender == null) defaultSender else sender)
      }
    }
  )

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*) {
    handler ! Rejected(rejections)
  }

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = Found) {
    complete {
      HttpResponse(
        status = redirectionType,
        headers = Location(uri) :: Nil,
        entity = redirectionType.htmlTemplate.toOption.map(s => HttpBody(`text/html`, s format uri))
      )
    }
  }

  /**
   * Completes the request with status "200 Ok" and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T :Marshaller](obj: T) {
    complete(OK, obj)
  }

  /**
   * Completes the request with the given status and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T :Marshaller](status: StatusCode, obj: T) {
    complete(status, Nil, obj)
  }

  /**
   * Completes the request with the given status, headers and the response entity created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def complete[T](status: StatusCode, headers: List[HttpHeader], obj: T)(implicit marshaller: Marshaller[T]) {
    marshaller(request.acceptableContentType) match {
      case Right(marshalling) => marshalling(obj, new DefaultMarshallingContext(status, headers))
      case Left(acceptableContentTypes) => reject(UnacceptedResponseContentTypeRejection(acceptableContentTypes))
    }
  }

  /**
   * Completes the request with the given status and the respective default message in the entity.
   */
  def complete(status: StatusCode) {
    complete(status: HttpResponse)
  }

  /**
   * Schedules the completion of the request with result of the given future.
   */
  def complete(future: Future[HttpResponse]) {
    future.onComplete {
      case Right(response) => complete(response)
      case Left(error) => throw error
    }
  }

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse) {
    handler ! response
  }

  /**
   * Creates an HttpException with the given properties and bubbles it up the response chain,
   * where it is dealt with by the closest `handleExceptions` directive and its ExceptionHandler.
   */
  def fail(status: StatusCode, message: String = "") {
    fail(HttpException(status, message))
  }

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def fail(error: Throwable) {
    handler ! Status.Failure(error)
  }

  private class DefaultMarshallingContext(status: StatusCode, headers: List[HttpHeader]) extends MarshallingContext {
    def marshalTo(entity: HttpEntity) { complete(response(entity)) }
    def handleError(error: Throwable) { fail(error) }
    def startChunkedMessage(entity: HttpEntity)(implicit sender: ActorRef) = {
      handler.tell(ChunkedResponseStart(response(entity)), sender)
      handler
    }
    def response(entity: HttpEntity) = HttpResponse(status, entity, headers)
  }
}

case class Rejected(rejections: Seq[Rejection]) {
  def map(f: Rejection => Rejection) = Rejected(rejections.map(f))
  def flatMap(f: Rejection => GenTraversableOnce[Rejection]) = Rejected(rejections.flatMap(f))
}