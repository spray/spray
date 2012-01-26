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
 * The [[cc.spray.can.HttpServer]] dispatches a `RequestContext` instance to the service actor (as configured in the
 * [[cc.spray.can.ServerConfig]] of the [[cc.spray.can.HttpServer]]) upon successful reception of an HTTP request.
 * The service actor is expected to either complete the request by calling `responder.complete` or start a chunked
 * response by calling `responder.startChunkedResponse`. If neither of this happens within the timeout period configured
 * as `requestTimeout` in the [[cc.spray.can.ServerConfig]] the [[cc.spray.can.HttpServer]] actor dispatches a
 * [[cc.spray.can.Timeout]] instance to the configured timeout actor.
 */
case class RequestContext(
  request: HttpRequest,
  remoteAddress: InetAddress,
  responder: RequestResponder
)