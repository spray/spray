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
import typeconversion.{MarshallingContext, ChunkSender}

/**
 * Encapsulates the completion logic of a spray route.
 */
trait RequestResponder {

  /**
   * Completes the request with the given RoutingResult.
   */
  def complete: HttpResponse => Unit

  /**
   * Rejects the request with the given set of Rejections (which might be empty).
   */
  def reject: Set[Rejection] => Unit

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse): ChunkSender

  /**
   * Explicitly resets the connection idle timeout for the connection underlying this response.
   */
  def resetConnectionTimeout()

  /**
   * Returns a copy of this responder with the complete method replaced by the given one.
   */
  def withComplete(newComplete: HttpResponse => Unit): RequestResponder

  /**
   * Returns a copy of this responder with the reject method replaced by the given one.
   */
  def withReject(newReject: Set[Rejection] => Unit): RequestResponder

  /**
   * Returns a copy of this responder registering the given callback function to be invoked if and when the client
   * prematurely closed the connection.
   */
  def withOnClientClose(callback: () => Unit): RequestResponder
}

object RequestResponder {
  lazy val EmptyResponder = new SimpleResponder(_ => (), _ => ())
}

/**
 * A RequestResponder throwing UnsupportedOperationExceptions for the `startChunkedResponse`, `onClientClose` and
 * `resetConnectionTimeout` methods.
 */
class SimpleResponder(val complete: HttpResponse => Unit, val reject: Set[Rejection] => Unit) extends RequestResponder {
  def startChunkedResponse(response: HttpResponse) = throw new UnsupportedOperationException
  def resetConnectionTimeout() { throw new UnsupportedOperationException }
  def withComplete(newComplete: HttpResponse => Unit) = new SimpleResponder(newComplete, reject)
  def withReject(newReject: Set[Rejection] => Unit) = new SimpleResponder(complete, newReject)
  def withOnClientClose(callback: () => Unit) = this
}