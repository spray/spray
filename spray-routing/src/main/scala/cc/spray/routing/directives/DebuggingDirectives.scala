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
import akka.event.Logging._
import cc.spray.util.identityFunc
import cc.spray.http._


trait DebuggingDirectives {
  import BasicDirectives._

  def log: LoggingAdapter

  def logRequest(marker: String = "", level: LogLevel = DebugLevel): Directive0 =
    mapRequest(logMessage("Request", marker, level))

  def logHttpResponse(marker: String = "", level: LogLevel = DebugLevel): Directive0 =
    mapHttpResponse(logMessage("Response", marker, level))

  def logRouteResponse(marker: String = "", level: LogLevel = DebugLevel): Directive0 =
    mapRouteResponse(logMessage("Response", marker, level))

  def logRequestResponse(marker: String = "", level: LogLevel = DebugLevel,
                         showRequest: HttpRequest => Any = identityFunc,
                         showResponse: HttpResponse => Any = identityFunc): Directive0 =
    mapRequestContext { ctx =>
      val mark = if (marker.isEmpty) marker else " " + marker
      val request2Show = showRequest(ctx.request)
      log.log(level, "Request{}: {}", mark, request2Show)
      ctx.mapRouteResponse { msg =>
        msg match {
          case response: HttpResponse =>
            log.log(level, "Completed{}:\n  Request: {}\n  Response: {}", mark, request2Show, showResponse(response))
          case Rejected(rejections) =>
            log.log(level, "Rejected{}:\n Request: {}\n  Rejections: {}", mark, request2Show, rejections)
          case other =>
            log.log(level, "Route response{}:\n  Request: {}\n  Response: {}", mark, request2Show, other)
        }
        msg
      }
    }

  private def logMessage[T](prefix: String, marker: String, level: LogLevel)(msg: T): T = {
    log.log(level, "{}: {}", if (marker.isEmpty) prefix else prefix + ' ' + marker, msg)
    msg
  }
}