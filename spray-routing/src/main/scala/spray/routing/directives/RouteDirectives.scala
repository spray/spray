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

import shapeless.HList
import spray.httpx.marshalling.Marshaller
import spray.http._
import StatusCodes._


trait RouteDirectives {

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.reject(rejections: _*)}
  }

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = Found): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.redirect(uri, redirectionType) }
  }

  /**
   * Completes the request with status "200 Ok" and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T :Marshaller](obj: T): StandardRoute = complete(OK, obj)

  /**
   * Completes the request with the given status and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T :Marshaller](status: StatusCode, obj: T): StandardRoute = complete(status, Nil, obj)

  /**
   * Completes the request with the given status, headers and the response entity created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def complete[T :Marshaller](status: StatusCode, headers: List[HttpHeader], obj: T): StandardRoute =
    new StandardRoute {
      def apply(ctx: RequestContext) { ctx.complete(status, headers, obj) }
    }

  /**
   * Completes the request with the given [[spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.complete(response) }
  }

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def failWith(error: Throwable): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.failWith(error) }
  }

}

object RouteDirectives extends RouteDirectives


/**
 * A Route that can be implicitly converted into a Directive (fitting any signature).
 */
trait StandardRoute extends Route {
  def toDirective[L <: HList]: Directive[L] = StandardRoute.toDirective(this)
}

object StandardRoute {
  def apply(route: Route): StandardRoute = route match {
    case x: StandardRoute => x
    case x => new StandardRoute { def apply(ctx: RequestContext) { x(ctx) } }
  }

  /**
   * Converts the route into a directive that never passes the request to its inner route
   * (and always returns its underlying route).
   */
  implicit def toDirective[L <: HList](route: Route) = new Directive[L] {
    def happly(f: L => Route) = route
  }
}