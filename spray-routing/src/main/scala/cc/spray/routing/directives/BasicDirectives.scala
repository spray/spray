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

import akka.actor.ActorRef
import shapeless._
import cc.spray.http._


trait BasicDirectives {

  def mapInnerRoute(f: Route => Route): Directive0 = new Directive0 {
    def happly(inner: HNil => Route) = f(inner(HNil))
  }

  def mapRequestContext(f: RequestContext => RequestContext): Directive0 =
    mapInnerRoute { inner => ctx => inner(f(ctx)) }

  def mapRequest(f: HttpRequest => HttpRequest): Directive0 =
    mapRequestContext(_.mapRequest(f))

  def mapHandler(f: ActorRef => ActorRef): Directive0 =
    mapRequestContext(_.mapHandler(f))

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

  def mapRejections(f: Seq[Rejection] => Seq[Rejection]): Directive0 =
    mapRequestContext(_.mapRejections(f))

  // TODO: remove implicit parameter by introducing a magnet
  def filter[T <: HList](f: RequestContext => FilterResult[T])
                        (implicit fdb: FilteringDirectiveBuilder[T]): fdb.Out = fdb(f)
}

object BasicDirectives extends BasicDirectives


sealed abstract class FilteringDirectiveBuilder[T <: HList] {
  type Out <: Directive[T]
  def apply(f: RequestContext => FilterResult[T]): Out
}

object FilteringDirectiveBuilder extends LowPriorityFilteringDirectiveBuilder {
  implicit val fdb0 = new FilteringDirectiveBuilder[HNil] {
    type Out = Directive0
    def apply(filter: RequestContext => FilterResult[HNil]) = BasicDirectives.mapInnerRoute { inner => ctx =>
      filter(ctx) match {
        case Pass(HNil, transform) => inner(transform(ctx))
        case Reject(rejections) => ctx.reject(rejections: _*)
      }
    }
  }
}

sealed abstract class LowPriorityFilteringDirectiveBuilder {
  implicit def fdb[L <: HList] = new FilteringDirectiveBuilder[L] {
    type Out = Directive[L]
    def apply(filter: RequestContext => FilterResult[L]) = new Out {
      def happly(inner: L => Route) = { ctx =>
        filter(ctx) match {
          case Pass(values, transform) => inner(values)(transform(ctx))
          case Reject(rejections) => ctx.reject(rejections: _*)
        }
      }
    }
  }
}