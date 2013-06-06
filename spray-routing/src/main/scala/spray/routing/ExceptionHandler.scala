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

import akka.util.NonFatal
import spray.util.LoggingContext
import spray.http._
import StatusCodes._

trait ExceptionHandler extends ExceptionHandler.PF

object ExceptionHandler {
  type PF = PartialFunction[Throwable, Route]

  implicit def fromPF(pf: PF): ExceptionHandler =
    new ExceptionHandler {
      def isDefinedAt(error: Throwable) = pf.isDefinedAt(error)
      def apply(error: Throwable) = pf(error)
    }

  implicit def default(implicit settings: RoutingSettings, log: LoggingContext): ExceptionHandler =
    fromPF {
      case e: IllegalRequestException ⇒ ctx ⇒
        log.warning("Illegal request {}\n\t{}\n\tCompleting with '{}' response",
          ctx.request, e.getMessage, e.status)
        ctx.complete(e.status, e.info.format(settings.verboseErrorMessages))

      case e: RequestProcessingException ⇒ ctx ⇒
        log.warning("Request {} could not be handled normally\n\t{}\n\tCompleting with '{}' response",
          ctx.request, e.getMessage, e.status)
        ctx.complete(e.status, e.info.format(settings.verboseErrorMessages))

      case NonFatal(e) ⇒ ctx ⇒
        log.error(e, "Error during processing of request {}", ctx.request)
        ctx.complete(InternalServerError)
    }
}
