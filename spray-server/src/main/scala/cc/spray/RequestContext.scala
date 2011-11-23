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
import typeconversion._
import akka.dispatch.Future

/**
 * Immutable object encapsulating the context of an [[cc.spray.http.HttpRequest]]
 * as it flows through a ''spray'' Route structure.
 */
case class RequestContext(
  request: HttpRequest,
  responder: RequestResponder,
  remoteHost: HttpIp = "127.0.0.1",
  unmatchedPath: String = ""
) {

  /**
   * Returns a copy of this context with the HttpRequest transformed by the given function.
   */
  def withRequestTransformed(f: HttpRequest => HttpRequest): RequestContext = {
    val newRequest = f(request)
    if (newRequest eq request) this else copy(request = newRequest)
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into the responder.
   */
  def withResponseTransformed(f: HttpResponse => HttpResponse): RequestContext =
    withComplete(response => responder.complete(f(response)))

  /**
   * Returns a copy of this context with the given rejection transformation function chained into the responder.
   */
  def withRejectionsTransformed(f: Set[Rejection] => Set[Rejection]): RequestContext =
    withReject(rejections => responder.reject(f(rejections)))

  /**
   * Returns a copy of this context with a new responder using the given complete function.
   */
  def withComplete(newComplete: HttpResponse => Unit): RequestContext =
    withResponderTransformed(_.withComplete(newComplete))

  /**
   * Returns a copy of this context with a new responder using the given complete function.
   */
  def withReject(newReject: Set[Rejection] => Unit): RequestContext =
    withResponderTransformed(_.withReject(newReject))

  /**
   * Returns a copy of this context with the responder transformed by the given function.
   */
  def withResponderTransformed(f: RequestResponder => RequestResponder) = copy(responder = f(responder))

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*) {
    reject(Set(rejections: _*))
  }
  
  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Set[Rejection]) {
    responder.reject(rejections)
  }

  /**
   * Schedules the completion of the request with status "200 Ok" and the response content created by marshalling the
   * future result using the in-scope marshaller for A.
   */
  def complete[A :Marshaller](responseFuture: Future[A]) {
    responseFuture.onComplete(future => complete(future.resultOrException.get))
  }

  /**
   * Completes the request with status "200 Ok" and the response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[A :Marshaller](obj: A) {
    complete(OK, obj)
  }

  /**
   * Completes the request with the given status and the response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[A :Marshaller](status: StatusCode, obj: A) {
    complete(status, Nil, obj)
  }

  /**
   * Completes the request with the given status, headers and the response content created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def complete[A :Marshaller](status: StatusCode, headers: List[HttpHeader], obj: A) {
    marshaller.apply(request.acceptableContentType) match {
      case MarshalWith(converter) => converter(marshallingContext(status, headers)).apply(obj)
      case CantMarshal(onlyTo) => reject(UnacceptedResponseContentTypeRejection(onlyTo))
    }
  }

  /**
   * Creates a MarshallingContext using the given status and headers.
   */
  private[spray] def marshallingContext(status: StatusCode, headers: List[HttpHeader]) = {
    new MarshallingContext {
      def marshalTo(content: HttpContent) { complete(HttpResponse(status, headers, content)) }
      def startChunkedMessage(contentType: ContentType) =
        startChunkedResponse(HttpResponse(status, headers, HttpContent(contentType, utils.EmptyByteArray)))
    }
  }

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse) {
    responder.complete(response)
  }

  /**
   * Returns a copy of this context that cancels all rejections of type R with
   * a [[cc.spray.RejectionRejection]]. 
   */
  def cancelRejectionsOfType[R <: Rejection :ClassManifest]: RequestContext = {
    val erasure = classManifest.erasure
    cancelRejections(erasure.isInstance(_))
  }
  
  /**
   * Returns a copy of this context that cancels all rejections matching the given predicate with
   * a [[cc.spray.RejectionRejection]].
   */
  def cancelRejections(reject: Rejection => Boolean): RequestContext =
    withReject(rejections => responder.reject(rejections + RejectionRejection(reject)))

  /**
   * Completes the request with the given [[cc.spray.http.HttpFailure]].
   */
  def fail(status: HttpFailure) {
    fail(status, status.defaultMessage)(DefaultMarshallers.StringMarshaller)
  }

  /**
   * Completes the request with the given status and the response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def fail[A :Marshaller](status: HttpFailure, obj: A) {
    fail(status, Nil, obj)
  }

  /**
   * Completes the request with the given status, headers and the response content created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def fail[A :Marshaller](status: HttpFailure, headers: List[HttpHeader], obj: A) {
    complete(status, headers, obj)
  }
  
  /**
   * Completes the request with a 301 redirection response to the given URI.
   */
  def redirect(uri: String, redirectionType: Redirection = Found) {
    complete {
      HttpResponse(
        status = redirectionType,
        headers = Location(uri) :: Nil,
        content = HttpContent(`text/html`,
          "The requested resource temporarily resides under this <a href=\"" + uri + "\">URI</a>.")
      )
    }
  }

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse) = responder.startChunkedResponse(response)
}