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

import akka.actor.ActorRef
import shapeless._
import spray.util._
import spray.http._


trait BasicDirectives {

  def mapInnerRoute(f: Route => Route): Directive0 = new Directive0 {
    def happly(inner: HNil => Route) = f(inner(HNil))
  }

  def mapRequestContext(f: RequestContext => RequestContext): Directive0 =
    mapInnerRoute { inner => ctx => inner(f(ctx)) }

  def mapRequest(f: HttpRequest => HttpRequest): Directive0 =
    mapRequestContext(_.mapRequest(f))

  def mapResponder(f: ActorRef => ActorRef): Directive0 =
    mapRequestContext(_.mapResponder(f))

  def mapRouteResponse(f: Any => Any): Directive0 =
    mapRequestContext(_.mapRouteResponse(f))

  def mapRouteResponsePF(f: PartialFunction[Any, Any]): Directive0 =
    mapRequestContext(_.mapRouteResponsePF(f))

  def flatMapRouteResponse(f: Any => Seq[Any]): Directive0 =
    mapRequestContext(_.flatMapRouteResponse(f))

  def flatMapRouteResponsePF(f: PartialFunction[Any, Seq[Any]]): Directive0 =
    mapRequestContext(_.flatMapRouteResponsePF(f))

  def mapHttpResponse(f: HttpResponse => HttpResponse): Directive0 =
    mapRequestContext(_.mapHttpResponse(f))

  def mapHttpResponseEntity(f: HttpEntity => HttpEntity): Directive0 =
    mapRequestContext(_.mapHttpResponseEntity(f))

  def mapHttpResponseHeaders(f: List[HttpHeader] => List[HttpHeader]): Directive0 =
    mapRequestContext(_.mapHttpResponseHeaders(f))

  def mapRejections(f: List[Rejection] => List[Rejection]): Directive0 =
    mapRequestContext(_.mapRejections(f))

  def filter[L <: HList](f: RequestContext => FilterResult[L]): Directive[L] =
    new Directive[L] {
      def happly(inner: L => Route) = { ctx =>
        f(ctx) match {
          case Pass(values, transform) => inner(values)(transform(ctx))
          case Reject(rejections) => ctx.reject(rejections: _*)
        }
      }
    }

  /**
   * A Directive0 that always passes the request on to its inner route
   * (i.e. does nothing with the request or the response).
   */
  def noop: Directive0 = mapInnerRoute(identityFunc)

  /**
   * Injects the given value into a directive.
   */
  def provide[T](value: T): Directive[T :: HNil] = hprovide(value :: HNil)

  /**
   * Injects the given values into a directive.
   */
  def hprovide[L <: HList](values: L): Directive[L] = new Directive[L] {
    def happly(f: L => Route) = f(values)
  }

  /**
   * Extracts a single value using the given function.
   */
  def extract[T](f: RequestContext => T): Directive[T :: HNil] =
    hextract(ctx => f(ctx) :: HNil)

  /**
   * Extracts a number of values using the given function.
   */
  def hextract[L <: HList](f: RequestContext => L): Directive[L] = new Directive[L] {
    def happly(inner: L => Route) = ctx => inner(f(ctx))(ctx)
  }
}

object BasicDirectives extends BasicDirectives