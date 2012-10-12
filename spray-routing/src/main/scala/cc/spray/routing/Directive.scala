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


abstract class Directive[L <: HList] { self =>
  def happly(f: L => Route): Route

  def | (that: Directive[L]) =
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

  def & (concat: ConcatMagnet[L]) =
    new Directive[concat.Out] {
      def happly(f: concat.Out => Route) = self.happly(concat(f))
    }

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

  def unwrapFuture[R](implicit ev: L <:< (Future[R] :: HNil), hl: HListable[R]) =
    new Directive[hl.Out] {
      def happly(f: hl.Out => Route) = self.happly { list => ctx =>
        list.head.onComplete {
          case Right(values) => f(hl(values))(ctx)
          case Left(error) => ctx.failWith(error)
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

trait ConcatMagnet[L <: HList] {
  type Out <: HList
  def apply(f: Out => Route): L => Route
}

object ConcatMagnet {
  implicit def fromR[L <: HList, R <: HList](other: Directive[R])
                    (implicit p: Prepender[L, R]) = new ConcatMagnet[L] {
    type Out = p.Out
    def apply(f: Out => Route) = { prefix =>
      other.happly { suffix =>
        f(p(prefix, suffix))
      }
    }
  }
}