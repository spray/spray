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

import shapeless._
import akka.dispatch.Future
import spray.httpx.unmarshalling.MalformedContent


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


abstract class Directive[L <: HList] { self =>
  def happly(f: L => Route): Route

  def | (that: Directive[L]): Directive[L] =
    recover(rejections => directives.BasicDirectives.mapRejections(rejections ::: _) & that)

  def & (magnet: ConjunctionMagnet[L]): magnet.Out = magnet(this)

  def as[T](deserializer: HListDeserializer[L, T]): Directive[T :: HNil] =
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

  def hmap[R](f: L => R)(implicit hl: HListable[R]): Directive[hl.Out] =
    new Directive[hl.Out] {
      def happly(g: hl.Out => Route) = self.happly { values => g(hl(f(values))) }
    }

  def hflatMap[R <: HList](f: L => Directive[R]): Directive[R] =
    new Directive[R] {
      def happly(g: R => Route) = self.happly { values => f(values).happly(g) }
    }

  def hrequire(predicate: L => Boolean): Directive0 =
    new Directive0 {
      def happly(f: HNil => Route) =
        self.happly { values => ctx => if (predicate(values)) f(HNil)(ctx) else ctx.reject() }
    }

  def unwrapFuture[R](implicit ev: L <:< (Future[R] :: HNil), hl: HListable[R]) =
    new Directive[hl.Out] {
      def happly(f: hl.Out => Route) = self.happly { list => ctx =>
        list.head
          .map { value => f(hl(value))(ctx) }
          .onFailure { case error => ctx.failWith(error) }
      }
    }

  def recover(recovery: List[Rejection] => Directive[L]): Directive[L] =
    new Directive[L] {
      def happly(f: L => Route) = { ctx =>
        @volatile var rejectedFromInnerRoute = false
        self.happly({ list => c => rejectedFromInnerRoute = true; f(list)(c) }) {
          ctx.withRejectionHandling { rejections =>
            if (rejectedFromInnerRoute) ctx.reject(rejections: _*)
            else recovery(rejections).happly(f)(ctx)
          }
        }
      }
    }

  def recoverPF(recovery: PartialFunction[List[Rejection], Directive[L]]): Directive[L] =
    recover { rejections =>
      if (recovery.isDefinedAt(rejections)) recovery(rejections)
      else Route.toDirective(_.reject(rejections: _*))
    }
}

object Directive {
  implicit def pimpApply[L <: HList](directive: Directive[L])
                                    (implicit hac: ApplyConverter[L]): hac.In => Route = f => directive.happly(hac(f))

  implicit def pimpSingleValueModifiers[T](dir: Directive[T :: HNil]) = new SingleValueModifiers(dir)
  class SingleValueModifiers[T](underlying: Directive[T :: HNil]) {
    def map[R](f: T => R)(implicit hl: HListable[R]): Directive[hl.Out] =
      underlying.hmap { case value :: HNil => f(value) }

    def flatMap[R <: HList](f: T => Directive[R]): Directive[R] =
      underlying.hflatMap { case value :: HNil => f(value) }

    def require(predicate: T => Boolean) =
      underlying.hrequire { case value :: HNil => predicate(value) }
  }
}