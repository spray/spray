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

trait RequestResponder {

  /**
   * Completes or rejects the request with the given RoutingResult.
   */
  def reply: RoutingResult => Unit

  /**
   * Starts a chunked (streaming) response. The given [[cc.spray.HttpResponse]] object must have the protocol
   * `HTTP/1.1` and is allowed to contain an entity body. Should the body of the given `HttpResponse` be non-empty it
   * is sent immediately following the responses HTTP header section as the first chunk.
   * The application is required to use the returned [[cc.spray.ChunkedResponder]] instance to send any number of
   * response chunks before calling the `ChunkedResponder`s `close` method to finalize the response.
   */
  def startChunkedResponse(response: HttpResponse): ChunkedResponder = {
    throw new UnsupportedOperationException
  }

  /**
   * Registers the given function to be called if and when the client prematurely closed the connection.
   */
  def onClientClose(callback: () => Unit): this.type = {
    throw new UnsupportedOperationException
  }

  /**
   * Explicitly resets the connection idle timeout for the connection underlying this chunked response.
   */
  def resetConnectionTimeout() {
    throw new UnsupportedOperationException // default implementation
  }

  /**
   * Creates a copy of this responder with the reply function replaces by the given one.
   */
  def withReply(newReply: RoutingResult => Unit): RequestResponder
}

object RequestResponder {
  lazy val EmptyResponder = new SimpleResponder(_ => ())
}

/**
 * A RequestResponder throwing UnsupportedOperationExceptions for the `startChunkedResponse`, `onClientClose` and
 * `resetConnectionTimeout` methods.
 */
class SimpleResponder(val reply: RoutingResult => Unit) extends RequestResponder {
  def withReply(newReply: (RoutingResult) => Unit) = new SimpleResponder(newReply)
}

/**
 * A `ChunkedResponder` is returned by the `startChunkedResponse` method of a [[cc.spray.RequestResponder]]
 * (the `responder` member of a [[cc.spray.RequestContext]]). It is used by the application to send the chunks and
 * finalization of a chunked (streaming) HTTP response.
 */
trait ChunkedResponder {

  /**
   * Send the given [[cc.spray.can.MessageChunk]] back to the client and returns the sequence number of the chunk
   * (which can be used for example with the `onChunkSent` method).
   */
  def sendChunk(chunk: MessageChunk): Long

  /**
   * Finalizes the chunked (streaming) response.
   */
  def close(extensions: List[ChunkExtension] = Nil, trailer: List[HttpHeader] = Nil)

  /**
   * Registers the given function to be called whenever a chunk previously scheduled for sending via `sendChunk`
   * has actually and successfully gone out over the wire. The callback receives the sequence number as produced by
   * the `sendChunk` method.
   */
  def onChunkSent(callback: Long => Unit): this.type
}