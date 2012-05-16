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

package cc.spray
package directives

import akka.util.NonFatal
import akka.actor.{ActorLogging, ActorSystem, Props, Actor}

private[spray] trait ExecutionDirectives {
  this: BasicDirectives =>

  implicit def actorSystem: ActorSystem

  /**
   * Returns a Route that executes its inner Route in the content of a newly spawned actor.
   */
  def detach = transformRoute { route => ctx =>
    actorSystem.actorOf {
      Props {
        new Actor() with ErrorHandling with ActorLogging {
          def receive = {
            case ctx: RequestContext => try {
              route(ctx)
            } catch {
              case NonFatal(e :Exception) => ctx.complete(responseForException(ctx.request, e))
            } finally {
              context.stop(self)
            }
          }
        }
      }
    } ! ctx
  }

  /**
   * A directive thats evaluates its inner Route for every request anew. Note that this directive has no additional
   * effect, when used inside (or some level underneath) a directive extracting one or more values, since everything
   * inside a directive extracing values is _always_ reevaluted for every request.
   *
   * Also Note that this directive differs from most other directives in that it cannot be combined with other routes
   * via the usual `&` and `|` operators.
   */
  object dynamic {
    def apply(inner: => Route): Route = ctx => inner(ctx)
  }
}