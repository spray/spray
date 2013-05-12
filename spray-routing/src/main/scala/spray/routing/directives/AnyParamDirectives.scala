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

import shapeless._

trait AnyParamDirectives {
  /**
   * Extracts a parameter either from a form field or from query parameters (in that order), and passes the value(s)
   * to the inner route.
   *
   * Rejects the request if both form field and query parameter matcher(s) defined by the definition(s) don't match.
   */
  def anyParam(apdm: AnyParamDefMagnet): apdm.Out = apdm()

  /**
   * Extracts a parameter either from a form field or from query parameters (in that order), and passes the value(s)
   * to the inner route.
   *
   * Rejects the request if both form field and query parameter matcher(s) defined by the definition(s) don't match.
   */
  def anyParams(apdm: AnyParamDefMagnet): apdm.Out = apdm()
}

object AnyParamDirectives extends AnyParamDirectives

trait AnyParamDefMagnet {
  type Out
  def apply(): Out
}

object AnyParamDefMagnet {
  implicit def forString[T <: String](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forSymbol[T <: Symbol](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forNR[T <: NameReceptacle[_]](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forNDesR[T <: NameDeserializerReceptacle[_]](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forNDefR[T <: NameDefaultReceptacle[_]](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forNDesDefR[T <: NameDeserializerDefaultReceptacle[_]](value: T)(implicit apdm2: AnyParamDefMagnet2[Tuple1[T]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forTuple[T <: Product](value: T)(implicit apdm21: AnyParamDefMagnet2[T]) = {
    new AnyParamDefMagnet {
      type Out = apdm21.Out
      def apply() = apdm21(value)
    }
  }
}

trait AnyParamDefMagnet2[T] {
  type Out
  def apply(value: T): Out
}

object AnyParamDefMagnet2 {
  implicit def forTuple[T <: Product, L <: HList, Out](implicit hla: HListerAux[T, L],
                                                       apdma: AnyParamDefMagnetAux[L]) = {
    new AnyParamDefMagnet2[T] {
      def apply(value: T) = apdma(hla(value))
      type Out = apdma.Out
    }
  }
}

trait AnyParamDefMagnetAux[L] {
  type Out
  def apply(value: L): Out
}

object AnyParamDefMagnetAux {
  implicit def forHList[L <: HList](implicit f: LeftFolder[L, Directive0, MapReduce.type]) = {
    new AnyParamDefMagnetAux[L] {
      type Out = f.Out
      def apply(value: L) = {
        value.foldLeft(BasicDirectives.noop)(MapReduce)
      }
    }
  }

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList](implicit fdma: FieldDefMagnetAux[T, Directive[LB]],
                                                                 pdma: ParamDefMagnetAux[T, Directive[LB]],
                                                                 ev: PrependAux[LA, LB, Out]) = {

      // see https://groups.google.com/forum/?fromgroups=#!topic/spray-user/HGEEdVajpUw
      def fdmaWrapper(t: T): Directive[LB] = fdma(t).hflatMap {
        case None :: HNil ⇒ pdma(t)
        case x            ⇒ BasicDirectives.hprovide(x)
      }

      at[Directive[LA], T] { (a, t) ⇒ a & (fdmaWrapper(t) | pdma(t)) }
    }
  }
}
