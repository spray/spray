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

import scala.collection.GenTraversableOnce
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import akka.actor.{ Status, ActorRef }
import akka.spray.UnregisteredActorRef
import spray.httpx.marshalling.{ MarshallingContext, Marshaller }
import spray.util._
import spray.http._
import StatusCodes._
import HttpHeaders._
import MediaTypes._

/**
 * Immutable object encapsulating the context of an [[spray.http.HttpRequest]]
 * as it flows through a ''spray'' Route structure.
 */
case class RequestContext(request: HttpRequest, responder: ActorRef, unmatchedPath: Uri.Path) {

  /**
   * Returns a copy of this context with the HttpRequest transformed by the given function.
   */
  def withRequestMapped(f: HttpRequest ⇒ HttpRequest): RequestContext = {
    val transformed = f(request)
    if (transformed eq request) this else copy(request = transformed)
  }

  /**
   * Returns a copy of this context with responder substituted for the given one.
   */
  def withResponder(newResponder: ActorRef) =
    if (newResponder eq responder) this else copy(responder = newResponder)

  /**
   * Returns a copy of this context with the responder transformed by the given function.
   */
  def withResponderMapped(f: ActorRef ⇒ ActorRef) =
    withResponder(f(responder))

  /**
   * Returns a copy of this context with the unmatchedPath transformed by the given function.
   */
  def withUnmatchedPathMapped(f: Uri.Path ⇒ Uri.Path) = {
    val transformed = f(unmatchedPath)
    if (transformed == unmatchedPath) this else copy(unmatchedPath = transformed)
  }

  /**
   * Returns a copy of this context that automatically sets the sender of all messages to its responder to the given
   * one, if no explicit sender is passed along from upstream.
   */
  def withDefaultSender(defaultSender: ActorRef) =
    withResponder {
      new UnregisteredActorRef(responder) {
        def handle(message: Any)(implicit sender: ActorRef) {
          responder.tell(message, if (sender == null) defaultSender else sender)
        }
      }
    }

  /**
   * Returns a copy of this context with the given function handling a part of the response space.
   */
  def withRouteResponseHandling(f: PartialFunction[Any, Unit]) =
    withResponder {
      new UnregisteredActorRef(responder) {
        def handle(message: Any)(implicit sender: ActorRef) {
          if (f.isDefinedAt(message)) f(message) else responder ! message
        }
      }
    }

  /**
   * Returns a copy of this context with the given response handling function chained into the response chain.
   */
  def withRouteResponseRouting(f: PartialFunction[Any, Route]) =
    withRouteResponseHandling(f.andThen(_(this)))

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withRouteResponseMapped(f: Any ⇒ Any) =
    withResponder {
      new UnregisteredActorRef(responder) {
        def handle(message: Any)(implicit sender: ActorRef) {
          responder ! f(message)
        }
      }
    }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withRouteResponseMappedPF(f: PartialFunction[Any, Any]) =
    withRouteResponseMapped(msg ⇒ if (f.isDefinedAt(msg)) f(msg) else msg)

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withRouteResponseMultiplied(f: PartialFunction[Any, Seq[Any]]) =
    withResponder {
      new UnregisteredActorRef(responder) {
        def handle(message: Any)(implicit sender: ActorRef) {
          if (f.isDefinedAt(message)) f(message).foreach(responder ! _)
          else responder ! message
        }
      }
    }

  /**
   * Returns a copy of this context with the given rejection handling function chained into the response chain.
   */
  def withRejectionHandling(f: List[Rejection] ⇒ Unit) =
    withRouteResponseHandling { case Rejected(rejections) ⇒ f(rejections) }

  /**
   * Returns a copy of this context with the given rejection transformation function chained into the response chain.
   */
  def withRejectionsMapped(f: List[Rejection] ⇒ List[Rejection]) =
    withRouteResponseMapped {
      case Rejected(rejections) ⇒ Rejected(f(rejections))
      case x                    ⇒ x
    }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withHttpResponsePartMapped(f: HttpResponsePart ⇒ HttpResponsePart) =
    withRouteResponseMapped {
      case x: HttpResponsePart                 ⇒ f(x)
      case Confirmed(x: HttpResponsePart, ack) ⇒ Confirmed(f(x), ack)
      case x                                   ⇒ x
    }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withHttpResponsePartMultiplied(f: HttpResponsePart ⇒ Seq[HttpResponsePart]) =
    withRouteResponseMultiplied {
      case x: HttpResponsePart ⇒ f(x)
      case Confirmed(x: HttpResponsePart, ack) ⇒
        val parts = f(x)
        parts.updated(parts.size - 1, Confirmed(parts.last, ack))
    }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withHttpResponseMapped(f: HttpResponse ⇒ HttpResponse) =
    withRouteResponseMapped {
      case x: HttpResponse                         ⇒ f(x)
      case ChunkedResponseStart(x)                 ⇒ ChunkedResponseStart(f(x))
      case Confirmed(ChunkedResponseStart(x), ack) ⇒ Confirmed(ChunkedResponseStart(f(x)), ack)
      case Confirmed(x: HttpResponse, ack)         ⇒ Confirmed(f(x), ack)
      case x                                       ⇒ x
    }

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withHttpResponseEntityMapped(f: HttpEntity ⇒ HttpEntity) =
    withHttpResponseMapped(_.mapEntity(f))

  /**
   * Returns a copy of this context with the given response transformation function chained into the response chain.
   */
  def withHttpResponseHeadersMapped(f: List[HttpHeader] ⇒ List[HttpHeader]) =
    withHttpResponseMapped(_.mapHeaders(f))

  /**
   * Removes a potentially existing Accept header from the request headers.
   */
  def withContentNegotiationDisabled =
    copy(request = request.withHeaders(request.headers filterNot (_.isInstanceOf[Accept])))

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejection: Rejection): Unit =
    responder ! Rejected(rejection :: Nil)

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): Unit =
    responder ! Rejected(rejections.toList)

  /**
   * Completes the request with redirection response of the given type to the given URI.
   */
  def redirect(uri: Uri, redirectionType: Redirection): Unit =
    complete {
      HttpResponse(
        status = redirectionType,
        headers = Location(uri) :: Nil,
        entity = redirectionType.htmlTemplate match {
          case ""       ⇒ EmptyEntity
          case template ⇒ HttpEntity(`text/html`, template format uri)
        })
    }

  /**
   * Completes the request with the given status code and its default message as the response entity.
   */
  def complete(status: StatusCode): Unit =
    complete(HttpResponse(status, entity = status.defaultMessage))

  /**
   * Completes the request with status "200 Ok" and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T: Marshaller](obj: T): Unit =
    complete(OK, obj)

  /**
   * Completes the request with the given status and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T: Marshaller](status: StatusCode, obj: T): Unit =
    complete(status, Nil, obj)

  /**
   * Completes the request with the given status, headers and the response entity created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def complete[T](status: StatusCode, headers: List[HttpHeader], obj: T)(implicit marshaller: Marshaller[T]): Unit =
    marshaller(obj, marshallingContext(status, headers))

  /**
   * Completes the request with the given [[spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse): Unit =
    responder ! response

  /**
   * Schedules the completion of the request with result of the given future.
   */
  def complete(future: Future[HttpResponse])(implicit ec: ExecutionContext): Unit =
    future.onComplete {
      case Success(response) ⇒ complete(response)
      case Failure(error)    ⇒ failWith(error)
    }

  /**
   * Bubbles the given error up the response chain where it is dealt with by the closest `handleExceptions`
   * directive and its ``ExceptionHandler``, unless the error is a ``RejectionError``. In this case the
   * wrapped rejection is unpacked and "executed".
   */
  def failWith(error: Throwable): Unit =
    responder ! {
      error match {
        case RejectionError(rejection) ⇒ Rejected(rejection :: Nil)
        case x                         ⇒ Status.Failure(x)
      }
    }

  /**
   * Creates a MarshallingContext using the given status code and response headers.
   */
  def marshallingContext(status: StatusCode, headers: List[HttpHeader]): MarshallingContext =
    new MarshallingContext {
      def tryAccept(contentType: ContentType) = request.acceptableContentType(contentType)
      def rejectMarshalling(onlyTo: Seq[ContentType]): Unit = { reject(UnacceptedResponseContentTypeRejection(onlyTo)) }
      def marshalTo(entity: HttpEntity): Unit = { complete(response(entity)) }
      def handleError(error: Throwable): Unit = { failWith(error) }
      def startChunkedMessage(entity: HttpEntity, sentAck: Option[Any])(implicit sender: ActorRef) = {
        val chunkStart = ChunkedResponseStart(response(entity))
        val wrapper = if (sentAck.isEmpty) chunkStart else Confirmed(chunkStart, sentAck.get)
        responder.tell(wrapper, sender)
        responder
      }
      def response(entity: HttpEntity) = HttpResponse(status, entity, headers)
    }
}

case class Rejected(rejections: List[Rejection]) {
  def map(f: Rejection ⇒ Rejection) = Rejected(rejections.map(f))
  def flatMap(f: Rejection ⇒ GenTraversableOnce[Rejection]) = Rejected(rejections.flatMap(f))
}