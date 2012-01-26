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

import akka.dispatch.Future
import model.{HttpHeader, ChunkExtension, MessageChunk}

/**
 * A `ChunkedResponder` is returned by the `startChunkedResponse` method of a [[cc.spray.can.RequestResponder]]
 * (the `responder` member of a [[cc.spray.can.RequestContext]]). It is used by the application to send the chunks and
 * finalization of a chunked (streaming) HTTP response.
 */
trait ChunkedResponder {
  /**
   * Sends the given [[cc.spray.can.MessageChunk]] back to the client and returns a Future that is completed when the
   * chunk has actually and successfully been dispatched to the network layer. Should the client prematurely close
   * the connection the future is completed with a [[cc.spray.can.ClientClosedConnectionException]]
   */
  def sendChunk(chunk: MessageChunk): Future[Unit]

  /**
   * Finalizes the chunked (streaming) response.
   */
  def close(extensions: List[ChunkExtension] = Nil, trailer: List[HttpHeader] = Nil)
}