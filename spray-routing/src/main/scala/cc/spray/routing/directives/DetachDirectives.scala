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

import cc.spray.routing.RequestContext
import akka.actor._


trait DetachDirectives {
  import BasicDirectives._

  protected def context: ActorContext

  /**
   * Executes its inner Route in the context of the given actor.
   * Note that the parameter is a by-Name parameter, so the argument expression is going to be
   * re-evaluated for every request anew.
   */
  def detachTo(serviceActor: Route => ActorRef): Directive0 =
    mapInnerRoute { route => ctx => serviceActor(route) ! ctx }

  /**
   * Returns a function creating a new SingleRequestServiceActor for a given Route.
   */
  def singleRequestServiceActor(implicit eh: ExceptionHandler, rh: RejectionHandler): Route => ActorRef =
    route => context.actorOf(Props(new SingleRequestServiceActor(route)))
}


/**
 * An HttpService actor that reacts to an incoming RequestContext message by running it in the given Route
 * before shutting itself down.
 */
class SingleRequestServiceActor(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler)
  extends Actor with HttpService with ActorLogging {

  val sealedRoute = sealRoute.apply(route)

  def receive = {
    case ctx: RequestContext =>
      try sealedRoute(ctx)
      finally context.stop(self)
  }
}