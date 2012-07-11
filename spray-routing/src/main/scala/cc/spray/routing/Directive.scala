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

import shapeless._
import cc.spray.http._

abstract class Directive[A <: HList] { self =>
  def happly(f: A => Route): Route

  def | (that: Directive[A]) = new Directive[A] {
    def happly(f: A => Route) = { ctx =>
      self.happly(f) {
        ctx.withResponseHandling {
          case Rejected(rejections) => that.happly(f) {
            ctx.withRejectionsTransformed(rejections ++ _)
          }
        }
      }
    }
  }

  def & [B <: HList](that: Directive[B])(implicit prepend : Prepend[A, B]) = new Directive[prepend.Out] {
    def happly(f: prepend.Out => Route) =
      that.happly { valuesB =>
        self.happly { valuesA =>
          f(valuesA ::: valuesB)
        }
      }
  }
}

object Directive {
  def wrapping(f: Route => Route) = new Directive0 {
    def apply(inner: Route) = f(inner)
  }
  def requestContextTransforming(f: RequestContext => RequestContext) = wrapping { inner => ctx =>
    inner(f(ctx))
  }
  def requestTransforming(f: HttpRequest => HttpRequest) = wrapping { inner => ctx =>
    inner(ctx.withRequestTransformed(f))
  }
  def routeResponseTransforming(f: Any => Any) = wrapping { inner => ctx =>
    inner(ctx.withRouteResponseTransformed(f))
  }
  def httpResponseTransforming(f: HttpResponse => HttpResponse) = wrapping { inner => ctx =>
    inner(ctx.withHttpResponseTransformed(f))
  }
  def responseTransformingPF(f: PartialFunction[Any, Any]) = wrapping { inner => ctx =>
    inner(ctx.withResponseTransformedPF(f))
  }
  def filtering[T <: HList](f: RequestContext => FilterResult[T])(implicit fdb: FilteringDirectiveBuilder[T]) = fdb(f)

  implicit def pimpApply[T <: HNil](directive: Directive[T])(implicit hac: HApplyConverter[T]): hac.Out = hac(directive)
}

abstract class Directive0 extends Directive[HNil] { self =>
  def happly(f: HNil => Route) = apply(f(HNil))
  def apply(inner: Route): Route
  override def | (that: Directive[HNil]) = new Directive0 {
    def apply(inner: Route) = { ctx =>
      self(inner) {
        ctx.withResponseHandling {
          case Rejected(rejections) => that.happly(_ => inner) {
            ctx.withRejectionsTransformed(rejections ++ _)
          }
        }
      }
    }
  }
}

sealed abstract class HApplyConverter[T <: HList] {
  type Out
  def apply(directive: Directive[T]): Out
}

object HApplyConverter {
  implicit def hac1[A]: HApplyConverter[A :: HNil] = new HApplyConverter[A :: HNil] {
    type Out = ((A) => Route) => Route
    def apply(directive: Directive[A :: HNil]): Out = { f =>
      directive.happly {
        case a :: HNil => f(a)
      }
    }
  }
}

sealed abstract class FilteringDirectiveBuilder[T <: HList] {
  type Out <: Directive[T]
  def apply(f: RequestContext => FilterResult[T]): Out
}

object FilteringDirectiveBuilder extends LowerPriorityFilteringDirectiveBuilders {
  implicit val fdb0 = new FilteringDirectiveBuilder[HNil] {
    type Out = Directive0
    def apply(filter: RequestContext => FilterResult[HNil]) = Directive.wrapping { inner => ctx =>
      filter(ctx) match {
        case _: Pass[_] => inner(ctx)
        case Reject(rejections) => ctx.reject(rejections: _*)
      }
    }
  }
}

sealed abstract class LowerPriorityFilteringDirectiveBuilders {
  implicit def fdb[T <: HList] = new FilteringDirectiveBuilder[T] {
    type Out = Directive[T]
    def apply(filter: RequestContext => FilterResult[T]) = new Out {
      def happly(inner: T => Route) = { ctx =>
        filter(ctx) match {
          case Pass(values) => inner(values)(ctx)
          case Reject(rejections) => ctx.reject(rejections: _*)
        }
      }
    }
  }
}