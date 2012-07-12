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


abstract class Directive[A <: HList] { self =>
  def happly(f: A => Route): Route

  def | (that: Directive[A]) = new Directive[A] {
    def happly(f: A => Route) = { ctx =>
      self.happly(f) {
        ctx.withRouteResponseHandling {
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
  implicit def pimpApply[T <: HNil](directive: Directive[T])(implicit hac: HApplyConverter[T]): hac.Out = hac(directive)
}

abstract class Directive0 extends Directive[HNil] { self =>
  def happly(f: HNil => Route) = apply(f(HNil))
  def apply(inner: Route): Route
  override def | (that: Directive[HNil]) = new Directive0 {
    def apply(inner: Route) = { ctx =>
      self(inner) {
        ctx.withRouteResponseHandling {
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