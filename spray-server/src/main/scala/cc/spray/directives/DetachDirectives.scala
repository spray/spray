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

import akka.actor.Actor
import utils.Logging

private[spray] trait DetachDirectives extends DefaultDetachedActorFactory {

  /**
   * Returns a Route that executes its inner Route in the content of a newly spawned actor. You can supply your own
   * implicit detachedActorFactory function to take control of the actual spawning.
   */
  def detach(route: Route)(implicit detachedActorFactory: Route => Actor): Route = { ctx =>
    Actor.actorOf(detachedActorFactory(route)).start ! ctx
  }
}

// introduces one more layer in the inheritance chain in order to lower the priority of the contained implicits
trait DefaultDetachedActorFactory {

  implicit object DefaultDetachedActorFactory extends (Route => Actor) {
    def apply(route: Route) = new DetachedRouteActor(route)
  }
}

/**
 * Actor used by the `detach` directive (if the DefaultDetachedActorFactory is used)
 */
class DetachedRouteActor(route: Route) extends Actor with Logging with ErrorLogging {  
  protected def receive = {
    case ctx: RequestContext => {
      try {
        route(ctx)
      } catch {
        case e: Exception => ctx.complete(responseForException(ctx.request, e))
      } finally {
        self.stop()
      }
    } 
  }
} 