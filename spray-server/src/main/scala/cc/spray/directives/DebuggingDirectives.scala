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

import http.{HttpResponse, HttpRequest, HttpMessage}
import akka.actor.ActorSystem

trait DebuggingDirectives {
  this: BasicDirectives with MiscDirectives =>

  def actorSystem: ActorSystem

  def logRequest(marker: String = "") = transformRequest(logMessage("Request", marker))

  def logResponse(marker: String = "") = transformResponse(logMessage("Response", marker))

  def logUnchunkedResponse(marker: String) = transformUnchunkedResponse(logMessage("Unchunked response", marker))

  def logChunkedResponse(marker: String) = transformChunkedResponse(logMessage("Chunked response", marker))

  def logRequestResponse(marker: String = "",
                         showRequest: HttpRequest => Any = util.identityFunc,
                         showResponse: HttpResponse => Any = util.identityFunc) = {
    transformRequestContext { ctx =>
      val mark = if (marker.isEmpty) marker else " " + marker
      val request2Show = showRequest(ctx.request)
      log.debug("Request{}: {}", mark, request2Show)
      ctx.withResponderTransformed { responder =>
        responder.copy(
          complete = { response =>
            log.debug("Completed{} {} with {}", mark, request2Show, showResponse(response))
            responder.complete(response)
          },
          reject = { rejections =>
            log.debug("Rejected{} {} with {}", mark, request2Show, rejections)
            responder.reject(rejections)
          },
          startChunkedResponse = { response =>
            log.debug("Started chunked response for{} {} with {}", mark, request2Show, showResponse(response))
            responder.startChunkedResponse(response)
          }
        )
      }
    }
  }

  private def logMessage[T <: HttpMessage[T]](prefix: String, marker: String)(msg: T) = {
    log.debug("{}: {}", if (marker.isEmpty) prefix else prefix + ' ' + marker, msg)
    msg
  }

  private def log = actorSystem.log
}