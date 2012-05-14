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

package cc.spray

import http._
import typeconversion.ChunkSender

/**
 * Encapsulates the completion logic of a spray route.
 */
case class RequestResponder(

  /**
   * Completes the request with the given RoutingResult.
   */
  complete: HttpResponse => Unit,

  /**
   * Rejects the request with the given set of Rejections (which might be empty).
   */
  reject: Set[Rejection] => Unit = _ => throw new IllegalStateException,

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  startChunkedResponse: HttpResponse => ChunkSender = _ => throw new UnsupportedOperationException,

  /**
   * Explicitly resets the connection idle timeout for the connection underlying this response.
   */
  resetConnectionTimeout: () => Unit = () => throw new UnsupportedOperationException

) {

  /**
   * Returns a copy of this responder with the complete member replaced by the given one.
   */
  def withComplete(f: HttpResponse => Unit) = copy(complete = f)

  /**
   * Returns a copy of this responder with the reject member replaced by the given one.
   */
  def withReject(f: Set[Rejection] => Unit) = copy(reject = f)

  /**
   * Returns a copy of this responder with the startChunkedResponse member replaced by the given one.
   */
  def withStartChunkedResponse(f: HttpResponse => ChunkSender) = copy(startChunkedResponse = f)

}