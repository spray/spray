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

import akka.event.LoggingAdapter
import akka.util.NonFatal
import cc.spray.http._


trait ExceptionHandler extends ExceptionHandler.PF

object ExceptionHandler {
  type PF = PartialFunction[Throwable, LoggingAdapter => Route]

  implicit def fromPF(pf: PF): ExceptionHandler =
    new ExceptionHandler {
      def isDefinedAt(error: Throwable) = pf.isDefinedAt(error)
      def apply(error: Throwable) = pf(error)
    }

  implicit val Default = fromPF {
    case HttpException(failure, msg) => log => ctx =>
      log.warning("Request {} could not be handled normally, completing with {} response ({})",
        ctx.request, failure.value, msg)
      HttpResponse(failure, msg)
    case NonFatal(e) => log => ctx =>
      log.error(e, "Error during processing of request {}", ctx.request)
      StatusCodes.InternalServerError :HttpResponse
  }

}
