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
package model

import java.net.InetAddress


/**
 * An instance of this class serves as argument to the `streamActorCreator` function of the
 * [[cc.spray.can.ServerConfig]].
 */
case class ChunkedRequestContext(request: HttpRequest, remoteAddress: InetAddress)

/**
 * Receiver actors (see the `sendAndReceive` method of the [[cc.spray.can.HttpConnection]]) need to be able to handle
 * `ChunkedResponseStart` messages, which signal the arrival of a chunked (streaming) response.
 */
case class ChunkedResponseStart(status: Int, headers: List[HttpHeader])

/**
 * Stream actors (see the `streamActorCreator` member of the [[cc.spray.can.ServerConfig]]) need to be able to handle
 * `ChunkedRequestEnd` messages, which represent the end of an incoming chunked (streaming) request.
 */
case class ChunkedRequestEnd(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader],
  responder: RequestResponder
)

/**
 * Receiver actors (see the `sendAndReceive` method of the [[cc.spray.can.HttpConnection]]) need to be able to handle
 * `ChunkedResponseEnd` messages, which represent the end of an incoming chunked (streaming) response.
 */
case class ChunkedResponseEnd(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader]
)