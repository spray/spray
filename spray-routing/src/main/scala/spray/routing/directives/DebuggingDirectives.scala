/*
 * Copyright (C) 2011-2013 spray.io
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
import spray.util.LoggingContext
import spray.http._
import akka.event.LoggingAdapter

trait DebuggingDirectives {
  import BasicDirectives._

  def logRequest(magnet: LoggingMagnet[HttpRequest ⇒ Unit]): Directive0 =
    mapRequest { request ⇒ magnet.f(request); request }

  def logResponse(magnet: LoggingMagnet[Any ⇒ Unit]): Directive0 =
    mapRouteResponse { response ⇒ magnet.f(response); response }

  def logRequestResponse(magnet: LoggingMagnet[HttpRequest ⇒ Any ⇒ Unit]): Directive0 =
    mapRequestContext { ctx ⇒
      val logResponse = magnet.f(ctx.request)
      ctx.withRouteResponseMapped { response ⇒ logResponse(response); response }
    }
}

object DebuggingDirectives extends DebuggingDirectives

case class LoggingMagnet[T](f: T)

object LoggingMagnet {
  implicit def forMessageFromMarker[T](marker: String)(implicit log: LoggingContext) =
    forMessageFromMarkerAndLevel[T](marker -> DebugLevel)

  implicit def forMessageFromMarkerAndLevel[T](tuple: (String, LogLevel))(implicit log: LoggingContext) =
    forMessageFromFullShow[T] {
      val (marker, level) = tuple
      Message ⇒ LogEntry(Message, marker, level)
    }

  implicit def forMessageFromShow[T](show: T ⇒ String)(implicit log: LoggingContext) =
    forMessageFromFullShow[T](msg ⇒ LogEntry(show(msg), DebugLevel))

  implicit def forMessageFromFullShow[T](show: T ⇒ LogEntry)(implicit log: LoggingContext): LoggingMagnet[T ⇒ Unit] =
    LoggingMagnet(show(_).logTo(log))

  implicit def forRequestResponseFromMarker(marker: String)(implicit log: LoggingContext) =
    forRequestResponseFromMarkerAndLevel(marker -> DebugLevel)

  implicit def forRequestResponseFromMarkerAndLevel(tuple: (String, LogLevel))(implicit log: LoggingContext) =
    forRequestResponseFromFullShow {
      val (marker, level) = tuple
      request ⇒ response ⇒ Some(
        LogEntry("Response for\n  Request : " + request + "\n  Response: " + response, marker, level))
    }

  implicit def forRequestResponseFromHttpResponsePartShow(show: HttpRequest ⇒ HttpResponsePart ⇒ Option[LogEntry])(implicit log: LoggingContext): LoggingMagnet[HttpRequest ⇒ Any ⇒ Unit] =
    LoggingMagnet { request ⇒
      val showResponse = show(request);
      {
        case HttpMessagePartWrapper(part: HttpResponsePart, _) ⇒ showResponse(part).foreach(_.logTo(log))
        case _ ⇒ None
      }
    }

  implicit def forRequestResponseFromFullShow(show: HttpRequest ⇒ Any ⇒ Option[LogEntry])(implicit log: LoggingContext): LoggingMagnet[HttpRequest ⇒ Any ⇒ Unit] =
    LoggingMagnet { request ⇒
      val showResponse = show(request)
      response ⇒ showResponse(response).foreach(_.logTo(log))
    }
}

case class LogEntry(obj: Any, level: LogLevel = DebugLevel) {
  def logTo(log: LoggingAdapter): Unit = {
    log.log(level, obj.toString)
  }
}

object LogEntry {
  def apply(obj: Any, marker: String, level: LogLevel): LogEntry =
    LogEntry(if (marker.isEmpty) obj else marker + ": " + obj, level)
}