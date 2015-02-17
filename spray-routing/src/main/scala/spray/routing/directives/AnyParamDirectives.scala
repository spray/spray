/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
  /* directive */ def anyParam(apdm: AnyParamDefMagnet): apdm.Out = apdm()

  /**
   * Extracts a parameter either from a form field or from query parameters (in that order), and passes the value(s)
   * to the inner route.
   *
   * Rejects the request if both form field and query parameter matcher(s) defined by the definition(s) don't match.
   */
  /* directive */ def anyParams(apdm: AnyParamDefMagnet): apdm.Out = apdm()
}

object AnyParamDirectives extends AnyParamDirectives

trait AnyParamDefMagnet {
  type Out
  def apply(): Out
}

object AnyParamDefMagnet {
  implicit def apply[T](value: T)(implicit apdm2: AnyParamDefMagnet2[T]) =
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(value)
    }
}

trait AnyParamDefMagnet2[T] {
  type Out
  def apply(value: T): Out
}

object AnyParamDefMagnet2 {
  import FieldDefMagnet2.FieldDefMagnetAux
  import ParamDefMagnet2.ParamDefMagnetAux

  private type FDMA[A, B] = FieldDefMagnetAux[A, B]
  private type PDMA[A, B] = ParamDefMagnetAux[A, B]
  private type APDM[A, B] = AnyParamDefMagnet2[A] { type Out = B }
  def APDM[A, B](f: A ⇒ B) = new AnyParamDefMagnet2[A] { type Out = B; def apply(a: A) = f(a) }

  private def extractAnyParam[A, B](f: A ⇒ Directive1[B]) = APDM[A, Directive1[B]](f)

  private def anyParamWrapper[A, B](a: A)(implicit fdma: FDMA[A, Directive1[B]], pdma: PDMA[A, Directive1[B]]): Directive1[B] = {
    // handle optional params
    // see https://groups.google.com/forum/?fromgroups=#!topic/spray-user/HGEEdVajpUw
    fdma(a).hflatMap {
      case None :: HNil ⇒ pdma(a)
      case x            ⇒ BasicDirectives.hprovide(x)
    } | pdma(a)
  }

  private def anyParamDefaultWrapper[A, B](a: A, default: ⇒ B)(implicit fdma: FDMA[A, Directive1[B]], pdma: PDMA[A, Directive1[B]]): Directive1[B] =
    anyParamWrapper(a).hflatMap {
      case None :: HNil ⇒ BasicDirectives.provide(default)
      case x            ⇒ BasicDirectives.hprovide(x)
    } | BasicDirectives.provide(default)

  implicit def forString(implicit fdma: FDMA[String, Directive1[String]],
                         pdma: PDMA[String, Directive1[String]]) = extractAnyParam[String, String](anyParamWrapper(_))

  implicit def forSymbol(implicit fdma: FDMA[Symbol, Directive1[String]],
                         pdma: PDMA[Symbol, Directive1[String]]) = extractAnyParam[Symbol, String](anyParamWrapper(_))

  implicit def forNR[T](implicit fdma: FDMA[NameReceptacle[T], Directive1[T]],
                        pdma: PDMA[NameReceptacle[T], Directive1[T]]) = extractAnyParam[NameReceptacle[T], T](anyParamWrapper(_))

  implicit def forNDefR[T](implicit fdma: FDMA[NameReceptacle[T], Directive1[T]],
                           pdma: PDMA[NameReceptacle[T], Directive1[T]]) =
    extractAnyParam[NameDefaultReceptacle[T], T](t ⇒ anyParamDefaultWrapper(NameReceptacle[T](t.name), t.default))

  implicit def forNDesR[T](implicit fdma: FDMA[NameDeserializerReceptacle[T], Directive1[T]],
                           pdma: PDMA[NameDeserializerReceptacle[T], Directive1[T]]) =
    extractAnyParam[NameDeserializerReceptacle[T], T](anyParamWrapper(_))

  implicit def forNDesDefR[T](implicit fdma: FDMA[NameDeserializerReceptacle[T], Directive1[T]],
                              pdma: PDMA[NameDeserializerReceptacle[T], Directive1[T]]) =
    extractAnyParam[NameDeserializerDefaultReceptacle[T], T](t ⇒ anyParamDefaultWrapper(NameDeserializerReceptacle[T](t.name, t.deserializer), t.default))

  implicit def forRVR[T](implicit fdma: FDMA[RequiredValueReceptacle[T], Directive1[T]],
                         pdma: PDMA[RequiredValueReceptacle[T], Directive1[T]]) =
    extractAnyParam[RequiredValueReceptacle[T], T](anyParamWrapper(_))

  implicit def forRVDR[T](value: T)(implicit fdma: FDMA[RequiredValueDeserializerReceptacle[T], Directive1[T]],
                                    pdma: PDMA[RequiredValueDeserializerReceptacle[T], Directive1[T]]) =
    extractAnyParam[RequiredValueDeserializerReceptacle[T], T](anyParamWrapper(_))

  implicit def forTuple[T <: Product, L <: HList, Out0](implicit hla: HListerAux[T, L], pdma: APDM[L, Out0]) =
    APDM[T, Out0](tuple ⇒ pdma(hla(tuple)))

  implicit def forHList[L <: HList](implicit f: LeftFolder[L, Directive0, MapReduce.type]) =
    APDM[L, f.Out](_.foldLeft(BasicDirectives.noop)(MapReduce))

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList](implicit pdma: APDM[T, Directive[LB]], ev: PrependAux[LA, LB, Out]) =
      at[Directive[LA], T] { (a, t) ⇒ a & pdma(t) }
  }
}
