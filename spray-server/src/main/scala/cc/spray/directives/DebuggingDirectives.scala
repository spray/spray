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
package directives

import http.HttpMessage
import utils.Logging

trait DebuggingDirectives {
  this: MiscDirectives with Logging =>

  lazy val logRequest: SprayRoute0 = logRequest("logged request")
  def logRequest(marker: String): SprayRoute0 =
    transformRequest(logMessage(marker))

  lazy val logResponse: SprayRoute0 = logResponse("logged response")
  def logResponse(marker: String): SprayRoute0 =
    transformResponse(logMessage(marker))

  lazy val logUnchunkedResponse: SprayRoute0 = logUnchunkedResponse("logged response (unchunked)")
  def logUnchunkedResponse(marker: String): SprayRoute0 =
    transformUnchunkedResponse(logMessage(marker))

  lazy val logChunkedResponse: SprayRoute0 = logChunkedResponse("logged response (chunked)")
  def logChunkedResponse(marker: String): SprayRoute0 =
    transformChunkedResponse(logMessage(marker))

  private def logMessage[T <: HttpMessage[T]](marker: String)(msg: T) = {
    log.debug("%s: %s", marker, msg)
    msg
  }

}