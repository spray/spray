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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import shapeless._
import spray.httpx.unmarshalling.MalformedContent


abstract class Directive[L <: HList] { self =>
  def happly(f: L => Route): Route

  def | (that: Directive[L]): Directive[L] =
    new Directive[L] {
      def happly(f: L => Route) = { ctx =>
        @volatile var rejectedFromInnerRoute = false
        self.happly({ list => c => rejectedFromInnerRoute = true; f(list)(c) }) {
          ctx.withRejectionHandling { rejections =>
            if (rejectedFromInnerRoute) ctx.reject(rejections: _*)
            else that.happly(f)(ctx.mapRejections(rejections ++ _))
          }
        }
      }
    }

  def & (magnet: ConjunctionMagnet[L]): magnet.Out = magnet(this)

  def as[T](deserializer: HListDeserializer[L, T]) =
    new Directive[T :: HNil] {
      def happly(f: T :: HNil => Route) =
        self.happly { values => ctx =>
          deserializer(values) match {
            case Right(t) => f(t :: HNil)(ctx)
            case Left(MalformedContent(msg, _)) => ctx.reject(ValidationRejection(msg))
            case Left(error) => ctx.reject(ValidationRejection(error.toString))
          }
        }
    }

  def map[R](f: L => R)(implicit hl: HListable[R]) =
    new Directive[hl.Out] {
      def happly(g: hl.Out => Route) = self.happly { values => g(hl(f(values))) }
    }

  def flatMap[R <: HList](f: L => Directive[R]) =
    new Directive[R] {
      def happly(g: R => Route) = self.happly { values => f(values).happly(g) }
    }

  def unwrapFuture[R](implicit ev: L <:< (Future[R] :: HNil), hl: HListable[R], ec: ExecutionContext) =
    new Directive[hl.Out] {
      def happly(f: hl.Out => Route) = self.happly { list => ctx =>
        list.head.onComplete {
          case Success(values) => f(hl(values))(ctx)
          case Failure(error) => ctx.failWith(error)
        }
      }
    }

  def require[T](predicate: T => Boolean)(implicit ev: L <:< (T :: HNil)) =
    hrequire { list => predicate(list.head) }

  def hrequire(predicate: L => Boolean) =
    new Directive0 {
      def happly(f: HNil => Route) =
        self.happly { values => ctx => if (predicate(values)) f(HNil)(ctx) else ctx.reject() }
    }
}

object Directive {
  implicit def pimpApply[L <: HList](directive: Directive[L])
                                    (implicit hac: ApplyConverter[L]): hac.In => Route = f => directive.happly(hac(f))
}

trait ConjunctionMagnet[L <: HList] {
  type Out
  def apply(underlying: Directive[L]): Out
}

object ConjunctionMagnet {
  implicit def fromDirective[L <: HList, R <: HList](other: Directive[R])(implicit p: Prepender[L, R]) =
    new ConjunctionMagnet[L] {
      type Out = Directive[p.Out]
      def apply(underlying: Directive[L]): Out =
        new Directive[p.Out] {
          def happly(f: p.Out => Route) =
            underlying.happly { prefix =>
              other.happly { suffix =>
                f(p(prefix, suffix))
              }
            }
        }
    }

  implicit def fromStandardRoute[L <: HList](route: StandardRoute) =
    new ConjunctionMagnet[L] {
      type Out = StandardRoute
      def apply(underlying: Directive[L]): Out = StandardRoute(underlying.happly(_ => route))
    }

  implicit def fromRouteGenerator[T, R <: Route](generator: T => R) = new ConjunctionMagnet[HNil] {
    type Out = RouteGenerator[T]
    def apply(underlying: Directive0): Out = { value =>
      underlying.happly(_ => generator(value))
    }
  }
}