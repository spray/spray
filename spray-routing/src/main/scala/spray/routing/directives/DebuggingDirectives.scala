/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing
package directives

import akka.event.Logging._
import spray.util.{LoggingContext, identityFunc}
import spray.http._


trait DebuggingDirectives {
  import BasicDirectives._

  def logRequest(mm: MarkerMagnet, level: LogLevel = DebugLevel): Directive0 =
    mapRequest(logMessage("Request", mm, level))

  def logHttpResponse(mm: MarkerMagnet, level: LogLevel = DebugLevel): Directive0 =
    mapHttpResponse(logMessage("Response", mm, level))

  def logRouteResponse(mm: MarkerMagnet, level: LogLevel = DebugLevel): Directive0 =
    mapRouteResponse(logMessage("Response", mm, level))

  def logRequestResponse(mm: MarkerMagnet, level: LogLevel = DebugLevel,
                         showRequest: HttpRequest => Any = identityFunc,
                         showResponse: HttpResponse => Any = identityFunc): Directive0 = {
    import mm._
    mapRequestContext { ctx =>
      val mark = if (marker.isEmpty) marker else " " + marker
      val request2Show = showRequest(ctx.request)
      log.log(level, "Request{}: {}", mark, request2Show)
      ctx.mapRouteResponse { msg =>
        msg match {
          case HttpMessagePartWrapper(response: HttpResponse, _) =>
            log.log(level, "Completed{}:\n  Request: {}\n  Response: {}", mark, request2Show, showResponse(response))
          case Rejected(rejections) =>
            log.log(level, "Rejected{}:\n Request: {}\n  Rejections: {}", mark, request2Show, rejections)
          case other =>
            log.log(level, "Route response{}:\n  Request: {}\n  Response: {}", mark, request2Show, other)
        }
        msg
      }
    }
  }

  private def logMessage[T](prefix: String, mm: MarkerMagnet, level: LogLevel)(msg: T): T = {
    import mm._
    log.log(level, "{}: {}", if (marker.isEmpty) prefix else prefix + ' ' + marker, msg)
    msg
  }
}

object DebuggingDirectives extends DebuggingDirectives


class MarkerMagnet(val marker: String, val log: LoggingContext)

object MarkerMagnet {
  implicit def apply(marker: String)(implicit log: LoggingContext) = new MarkerMagnet(marker, log)
}