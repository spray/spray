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
import akka.actor.Status


trait ExceptionDirectives {
  import BasicDirectives._

  def log: LoggingAdapter

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[cc.spray.routing.ExceptionHandler]].
   */
  def handleExceptions(handler: ExceptionHandler): Directive0 =
    transformInnerRoute {
      inner => ctx =>
        val handleError = handler andThen (_(log)(ctx))
        try inner {
          ctx.withRouteResponseHandling {
            case Status.Failure(error) if handleError.isDefinedAt(error) => handleError(error)
          }
        }
        catch handleError
    }

}