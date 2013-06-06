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

import akka.util.NonFatal
import akka.actor._

trait ExecutionDirectives {
  import BasicDirectives._

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[spray.routing.ExceptionHandler]].
   */
  def handleExceptions(handler: ExceptionHandler): Directive0 =
    mapInnerRoute { inner ⇒
      ctx ⇒
        def handleError = handler andThen (_(ctx.withContentNegotiationDisabled))
        try inner {
          ctx withRouteResponseHandling {
            case Status.Failure(error) if handler isDefinedAt error ⇒ handleError(error)
          }
        }
        catch handleError
    }

  /**
   * Transforms rejections produced by its inner route using the given
   * [[spray.routing.RejectionHandler]].
   */
  def handleRejections(handler: RejectionHandler): Directive0 =
    mapRequestContext { ctx ⇒
      ctx withRejectionHandling { rejections ⇒
        val filteredRejections = RejectionHandler.applyTransformations(rejections)
        if (handler isDefinedAt filteredRejections)
          handler(filteredRejections) {
            ctx.withContentNegotiationDisabled withRejectionHandling { r ⇒
              sys.error("The RejectionHandler for " + rejections + " must not itself produce rejections (received " + r + ")!")
            }
          }
        else ctx.reject(filteredRejections: _*)
      }
    }

  /**
   * A directive that evaluates its inner Route for every request anew. Note that this directive has no additional
   * effect when used inside (or some level underneath) a directive extracting one or more values, since everything
   * inside a directive extracting values is _always_ reevaluated for every request.
   *
   * Also Note that this directive differs from most other directives in that it cannot be combined with other routes
   * via the usual `&` and `|` operators.
   */
  def dynamic = dynamicIf(enabled = true)

  /**
   * A directive that evaluates its inner Route for every request anew, if the given enabled flag is true.
   * Note that this directive has no additional effect when used inside (or some level underneath) a directive
   * extracting one or more values, since everything inside a directive extracting values is _always_ reevaluated for
   * every request.
   *
   * Also Note that this directive differs from most other directives in that it cannot be combined with other routes
   * via the usual `&` and `|` operators.
   */
  case class dynamicIf(enabled: Boolean) {
    def apply(inner: ⇒ Route): Route =
      if (enabled) Route(ctx ⇒ inner(ctx)) else inner
  }

  /**
   * Executes its inner Route in the context of the actor returned by the given function.
   * Note that the parameter function is re-evaluated for every request anew.
   */
  def detachTo(serviceActor: Route ⇒ ActorRef): Directive0 =
    mapInnerRoute { route ⇒ ctx ⇒ serviceActor(route) ! ctx }

  /**
   * Returns a function creating a new SingleRequestServiceActor for a given Route.
   */
  def singleRequestServiceActor(implicit refFactory: ActorRefFactory): Route ⇒ ActorRef =
    route ⇒ refFactory.actorOf(Props(new SingleRequestServiceActor(route)))
}

object ExecutionDirectives extends ExecutionDirectives

/**
 * An HttpService actor that reacts to an incoming RequestContext message by running it in the given Route
 * before shutting itself down.
 */
class SingleRequestServiceActor(route: Route) extends Actor {
  def receive = {
    case ctx: RequestContext ⇒
      try route(ctx)
      catch { case NonFatal(e) ⇒ ctx.failWith(e) }
      finally context.stop(self)
  }
}
