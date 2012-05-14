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
package typeconversion

import http._
import akka.dispatch.Future

trait ChunkSender {

  /**
   * Sends the given [[cc.spray.http.MessageChunk]] back to the client and returns a Future that is completed when the
   * chunk has actually and successfully been dispatched to the network layer. Should the client prematurely close
   * the connection the future is completed with a [[cc.spray.ClientClosedConnectionException]]
   */
  def sendChunk(chunk: MessageChunk): Future[Unit]

  /**
   * Finalizes the chunked (streaming) response.
   */
  def close(extensions: List[ChunkExtension] = Nil, trailer: List[HttpHeader] = Nil)

}