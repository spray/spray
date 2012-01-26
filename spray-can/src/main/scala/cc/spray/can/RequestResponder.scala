/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can

import model.HttpResponse

/**
 * An instance of this trait is used by the application to complete incoming requests.
 */
trait RequestResponder {
  /**
   * Completes a request by responding with the given [[cc.spray.can.HttpResponse]]. Only the first invocation of
   * this method determines the response that is sent back to the client. All potentially following calls will trigger
   * an exception.
   */
  def complete(response: HttpResponse)

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.can.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.can.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse): ChunkedResponder

  /**
   * Explicitly resets the connection idle timeout for the connection underlying this chunked response.
   */
  def resetConnectionTimeout()
}