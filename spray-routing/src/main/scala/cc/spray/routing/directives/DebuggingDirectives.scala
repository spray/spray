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

package cc.spray.routing
package directives

import akka.event.LoggingAdapter
import cc.spray.util.identityFunc
import cc.spray.http._


trait DebuggingDirectives {
  import BasicDirectives._

  def log: LoggingAdapter

  def logRequest(marker: String = "") = mapRequest(logMessage("Request", marker))

  def logHttpResponse(marker: String = "") = mapHttpResponse(logMessage("Response", marker))

  def logRouteResponse(marker: String = "") = mapRouteResponse(logMessage("Response", marker))

  def logRequestResponse(marker: String = "",
                         showRequest: HttpRequest => Any = identityFunc,
                         showResponse: HttpResponse => Any = identityFunc) = mapRequestContext { ctx =>
    val mark = if (marker.isEmpty) marker else " " + marker
    val request2Show = showRequest(ctx.request)
    log.debug("Request{}: {}", mark, request2Show)
    ctx.mapRouteResponse { msg =>
      msg match {
        case response: HttpResponse =>
          log.debug("Completed{}:\n  Request: {}\n  Response: {}", mark, request2Show, showResponse(response))
        case Rejected(rejections) =>
          log.debug("Rejected{}:\n Request: {}\n  Rejections: {}", mark, request2Show, rejections)
        case other =>
          log.debug("Route response{}:\n  Request: {}\n  Response: {}", mark, request2Show, other)
      }
      msg
    }
  }

  private def logMessage[T](prefix: String, marker: String)(msg: T): T = {
    log.debug("{}: {}", if (marker.isEmpty) prefix else prefix + ' ' + marker, msg)
    msg
  }
}