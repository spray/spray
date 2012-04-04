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
    val transformed = f(request)
    if (transformed eq request) this else copy(request = transformed)
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into 'responder.complete'
   * as well as 'responder.startChunkedResponse'.
   */
  def withResponseTransformed(f: HttpResponse => HttpResponse) = withResponderTransformed { responder =>
    responder.copy(
      complete = { response => responder.complete(f(response)) },
      startChunkedResponse = { response => responder.startChunkedResponse(f(response)) }
    )
  }

  /**
   * Returns a copy of this context with the given response transformation function chained into 'responder.complete'.
   */
  def withUnchunkedResponseTransformed(f: HttpResponse => HttpResponse) =
    withComplete(response => responder.complete(f(response)))

  /**
   * Returns a copy of this context with the given response transformation function chained into
   * 'responder.startChunkedResponse'.
   */
  def withChunkedResponseTransformed(f: HttpResponse => HttpResponse) =
    withStartChunkedResponse(response => responder.startChunkedResponse(f(response)))

  /**
   * Returns a copy of this context with the given rejection transformation function chained into 'responder.reject'.
   */
  def withRejectionsTransformed(f: Set[Rejection] => Set[Rejection]) =
    withReject(rejections => responder.reject(f(rejections)))

  /**
   * Returns a copy of this context with a new responder using the given complete function.
   */
  def withComplete(f: HttpResponse => Unit): RequestContext =
    withResponderTransformed(_.withComplete(f))

  /**
   * Returns a copy of this context with a new responder using the given reject function.
   */
  def withReject(f: Set[Rejection] => Unit): RequestContext =
    withResponderTransformed(_.withReject(f))

  /**
   * Returns a copy of this context with a new responder using the given startChunkedResponse function.
   */
  def withStartChunkedResponse(f: HttpResponse => ChunkSender): RequestContext =
    withResponderTransformed(_.withStartChunkedResponse(f))

  /**
   * Returns a copy of this context with the responder transformed by the given function.
   */
  def withResponderTransformed(f: RequestResponder => RequestResponder) = {
    val transformed = f(responder)
    if (transformed eq responder) this else copy(responder = transformed)
  }

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

  private[spray] def marshallingContext(status: StatusCode, headers: List[HttpHeader]) = {
    new MarshallingContext {
      def marshalTo(content: HttpContent) { complete(HttpResponse(status, headers, content)) }
      def handleError(error: Throwable) { fail(error) }
      def startChunkedMessage(contentType: ContentType) =
        startChunkedResponse(HttpResponse(status, headers, HttpContent(contentType, util.EmptyByteArray)))
    }
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
   * Completes the request with a response corresponding to the given Throwable, with [[cc.spray.http.HttpException]]
   * instances receiving special handling.
   */
  def fail(error: Throwable) {
    error match {
      case HttpException(NotFound, NotFound.defaultMessage) => reject()
      case HttpException(failure, reason) => complete(HttpResponse(failure, reason))
      case e => complete(HttpResponse(InternalServerError, "Internal Server Error:\n" + e.toString))
    }
  }

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
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = Found) {
    import util._
    complete {
      HttpResponse(
        status = redirectionType,
        headers = Location(uri) :: Nil,
        content = redirectionType.htmlTemplate.toOption.map(s => HttpContent(`text/html`, s format uri)),
        protocol = HttpProtocols.`HTTP/1.1`
      )
    }
  }

  /**
   * Starts a chunked (streaming) response, of which the first chunk is produced by the marshaller in scope for the
   * given object. Note that the marshaller for `A` must not itself produce chunked responses or offload response
   * generation to another thread (or actor).
   */
  def startChunkedResponse[A :Marshaller](obj: A): Option[ChunkSender] =
    startChunkedResponse(OK, obj)

  /**
   * Starts a chunked (streaming) response, of which the first chunk is produced by the marshaller in scope for the
   * given object. Note that the marshaller for `A` must not itself produce chunked responses or offload response
   * generation to another thread (or actor).
   */
  def startChunkedResponse[A :Marshaller](status: StatusCode, obj: A): Option[ChunkSender] =
    startChunkedResponse(status, Nil, obj)

  /**
   * Starts a chunked (streaming) response, of which the first chunk is produced by the marshaller in scope for the
   * given object. Note that the marshaller for `A` must not itself produce chunked responses or offload response
   * generation to another thread (or actor).
   */
  def startChunkedResponse[A :Marshaller](status: StatusCode,
                                          headers: List[HttpHeader], obj: A): Option[ChunkSender] = {
    marshaller.apply(request.acceptableContentType) match {
      case MarshalWith(converter) =>
        var marshalled: Option[HttpContent] = None
        converter {
          new MarshallingContext {
            def marshalTo(content: HttpContent) { marshalled = Some(content) }
            def handleError(error: Throwable) { fail(error) }
            def startChunkedMessage(contentType: ContentType) = sys.error("Cannot use a marshaller for " +
              "'request.startChunkedResponse' that itself marshalls to a chunked response")
          }
        } apply(obj)
        marshalled.map(content => startChunkedResponse(HttpResponse(status, headers, content)))
          .orElse(sys.error("Marshaller did not immediately 'marshalTo' an HttpContent instance. Note that you " +
          "cannot use an asynchronous marshaller with 'startChunkedResponse'."))
      case CantMarshal(onlyTo) => reject(UnacceptedResponseContentTypeRejection(onlyTo)); None
    }
  }

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse): ChunkSender = responder.startChunkedResponse(response)
}