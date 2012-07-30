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

import java.lang.IllegalStateException
import cc.spray.http.QueryParams
import shapeless._


trait ParameterDirectives extends ToNameReceptaclePimps {

  /**
   * Extracts the requests query parameters as a Map[String, String].
   */
  def parameterMap: Directive[QueryParams :: HNil] =
    BasicDirectives.filter { ctx => Pass(ctx.request.queryParams :: HNil) }

  /**
   * Rejects the request if the query parameter matcher(s) defined by the definition(s) don't match.
   */
  def parameter(pdm: ParamDefMagnet): pdm.Out = pdm()

  /**
   * Rejects the request if the query parameter matcher(s) defined by the definition(s) don't match.
   */
  def parameters(pdm: ParamDefMagnet): pdm.Out = pdm()

}

object ParameterDirectives extends ParameterDirectives


trait ParamDefMagnet {
  type Out
  def apply(): Out
}

object ParamDefMagnet extends ToNameReceptaclePimps {
  import cc.spray.routing.directives.{ParamDefMagnetAux => PDMA}

  def apply[T](value: T)(implicit pdma: PDMA[T]) = new ParamDefMagnet {
    type Out = pdma.Out
    def apply() = pdma(value)
  }
  implicit def for1[T :PDMA](value: T) = apply[T](value)
  implicit def for2[T <: Product2[_, _] :PDMA](value: T) = apply(value)
  implicit def for3[T <: Product3[_, _, _] :PDMA](value: T) = apply(value)
  implicit def for4[T <: Product4[_, _, _, _] :PDMA](value: T) = apply(value)
  implicit def for5[T <: Product5[_, _, _, _, _] :PDMA](value: T) = apply(value)
  implicit def for6[T <: Product6[_, _, _, _, _, _] :PDMA](value: T) = apply(value)
  implicit def for7[T <: Product7[_, _, _, _, _, _, _] :PDMA](value: T) = apply(value)
  implicit def for8[T <: Product8[_, _, _, _, _, _, _, _] :PDMA](value: T) = apply(value)
  implicit def for9[T <: Product9[_, _, _, _, _, _, _, _, _] :PDMA](value: T) = apply(value)
}


trait ParamDefMagnetAux[T] {
  type Out
  def apply(value: T): Out
}

object ParamDefMagnetAux {
  import shapeless.{HList => HL, PrependAux => PA}

  type PDC[T, L <: HL] = ParamDefConverter[T, Directive[L]]

  implicit def for1[A, LA](implicit a: ParamDefConverter[A, LA]) =
    new ParamDefMagnetAux[A] {
      type Out = LA
      def apply(value: A) = a(value)
    }
  implicit def for2[A, B, LA <: HL, LB <: HL, R <: HL](implicit a: PDC[A, LA], b: PDC[B, LB], p: PA[LA, LB, R]) =
    new ParamDefMagnetAux[(A, B)] {
      type Out = Directive[R]
      def apply(t: (A, B)) = a(t._1) & b(t._2)
    }
  implicit def for3[A, B, C, LA <: HL, LB <: HL, LC <: HL, R1 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], p1: PA[LA, LB, R1], p2: PA[R1, LC, R]) =
    new ParamDefMagnetAux[(A, B, C)] {
      type Out = Directive[R]
      def apply(t: (A, B, C)) = a(t._1) & b(t._2) & c(t._3)
    }
  implicit def for4[A, B, C, D, LA <: HL, LB <: HL, LC <: HL, LD <: HL, R1 <: HL, R2 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R]) =
    new ParamDefMagnetAux[(A, B, C, D)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D)) = a(t._1) & b(t._2) & c(t._3) & d(t._4)
    }
  implicit def for5[A, B, C, D, E, LA <: HL, LB <: HL, LC <: HL, LD <: HL, LE <: HL, R1 <: HL, R2 <: HL, R3 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD], e: PDC[E, LE],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R3], p4: PA[R3, LE, R]) =
    new ParamDefMagnetAux[(A, B, C, D, E)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D, E)) = a(t._1) & b(t._2) & c(t._3) & d(t._4) & e(t._5)
    }
  implicit def for6[A, B, C, D, E, F, LA <: HL, LB <: HL, LC <: HL, LD <: HL, LE <: HL, LF <: HL, R1 <: HL, R2 <: HL, R3 <: HL, R4 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD], e: PDC[E, LE], f: PDC[F, LF],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R3], p4: PA[R3, LE, R4], p5: PA[R4, LF, R]) =
    new ParamDefMagnetAux[(A, B, C, D, E, F)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D, E, F)) = a(t._1) & b(t._2) & c(t._3) & d(t._4) & e(t._5) & f(t._6)
    }
  implicit def for7[A, B, C, D, E, F, G, LA <: HL, LB <: HL, LC <: HL, LD <: HL, LE <: HL, LF <: HL, LG <: HL, R1 <: HL, R2 <: HL, R3 <: HL, R4 <: HL, R5 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD], e: PDC[E, LE], f: PDC[F, LF], g: PDC[G, LG],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R3], p4: PA[R3, LE, R4], p5: PA[R4, LF, R5], p6: PA[R5, LG, R]) =
    new ParamDefMagnetAux[(A, B, C, D, E, F, G)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D, E, F, G)) = a(t._1) & b(t._2) & c(t._3) & d(t._4) & e(t._5) & f(t._6) & g(t._7)
    }
  implicit def for8[A, B, C, D, E, F, G, H, LA <: HL, LB <: HL, LC <: HL, LD <: HL, LE <: HL, LF <: HL, LG <: HL, LH <: HL, R1 <: HL, R2 <: HL, R3 <: HL, R4 <: HL, R5 <: HL, R6 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD], e: PDC[E, LE], f: PDC[F, LF], g: PDC[G, LG], h: PDC[H, LH],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R3], p4: PA[R3, LE, R4], p5: PA[R4, LF, R5], p6: PA[R5, LG, R6], p7: PA[R6, LH, R]) =
    new ParamDefMagnetAux[(A, B, C, D, E, F, G, H)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D, E, F, G, H)) = a(t._1) & b(t._2) & c(t._3) & d(t._4) & e(t._5) & f(t._6) & g(t._7) & h(t._8)
    }
  implicit def for9[A, B, C, D, E, F, G, H, I, LA <: HL, LB <: HL, LC <: HL, LD <: HL, LE <: HL, LF <: HL, LG <: HL, LH <: HL, LI <: HL, R1 <: HL, R2 <: HL, R3 <: HL, R4 <: HL, R5 <: HL, R6 <: HL, R7 <: HL, R <: HL]
    (implicit a: PDC[A, LA], b: PDC[B, LB], c: PDC[C, LC], d: PDC[D, LD], e: PDC[E, LE], f: PDC[F, LF], g: PDC[G, LG], h: PDC[H, LH], i: PDC[I, LI],
     p1: PA[LA, LB, R1], p2: PA[R1, LC, R2], p3: PA[R2, LD, R3], p4: PA[R3, LE, R4], p5: PA[R4, LF, R5], p6: PA[R5, LG, R6], p7: PA[R6, LH, R7], p8: PA[R7, LI, R]) =
    new ParamDefMagnetAux[(A, B, C, D, E, F, G, H, I)] {
      type Out = Directive[R]
      def apply(t: (A, B, C, D, E, F, G, H, I)) = a(t._1) & b(t._2) & c(t._3) & d(t._4) & e(t._5) & f(t._6) & g(t._7) & h(t._8) & i(t._9)
    }
}


trait ParamDefConverter[A, B] extends (A => B)

object ParamDefConverter {
  import cc.spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, _}

  /************ "regular" parameter extraction ******************/

  private def extractParameter[T, R](f: T => Directive[R :: HNil]) = new ParamDefConverter[T, Directive[R :: HNil]] {
    def apply(value: T) = f(value) 
  }    
  private def filter[R](paramName: String, fsod: FSOD[R]) = BasicDirectives.filter { ctx =>
    fsod(ctx.request.queryParams.get(paramName)) match {
      case Right(x) => Pass(x :: HNil)
      case Left(ContentExpected) => Reject(MissingQueryParamRejection(paramName))
      case Left(MalformedContent(errorMsg)) => Reject(MalformedQueryParamRejection(errorMsg, paramName))
      case Left(x: UnsupportedContentType) => throw new IllegalStateException(x.toString)
    }
  } 
  implicit def forString(implicit fsod: FSOD[String]) =
    extractParameter[String, String](s => filter(s, fsod))
  implicit def forSymbol(implicit fsod: FSOD[String]) =
    extractParameter[Symbol, String](s => filter(s.name, fsod))
  implicit def forNDesR[T] =
    extractParameter[NameDeserializerReceptacle[T], T](nr => filter(nr.name, nr.deserializer))
  implicit def forNDefR[T](implicit fsod: FSOD[T]) =
    extractParameter[NameDefaultReceptacle[T], T](nr => filter(nr.name, fsod.withDefaultValue(nr.default)))
  implicit def forNDesDefR[T] =
    extractParameter[NameDeserializerDefaultReceptacle[T], T](nr => filter(nr.name, nr.deserializer.withDefaultValue(nr.default)))
  implicit def forNR[T](implicit fsod: FSOD[T]) =
    extractParameter[NameReceptacle[T], T](nr => filter(nr.name, fsod))


  /************ required parameter support ******************/

  private def requireParameter[T](f: T => Directive0) = new ParamDefConverter[T, Directive0] {
    def apply(value: T) = f(value)
  }
  private def requiredFilter(paramName: String, fsod: FSOD[_], requiredValue: Any): Directive0 =
    BasicDirectives.filter { ctx =>
      fsod(ctx.request.queryParams.get(paramName)) match {
        case Right(value) if value == requiredValue => Pass.Empty
        case _ => Reject.Empty
      }
    }
  implicit def forRVR[T](implicit fsod: FSOD[T]) =
    requireParameter[RequiredValueReceptacle[T]](rvr => requiredFilter(rvr.name, fsod, rvr.requiredValue))
  implicit def forRVDR[T] =
    requireParameter[RequiredValueDeserializerReceptacle[T]](rvr => requiredFilter(rvr.name, rvr.deserializer, rvr.requiredValue))


  /************ HListed parameter definition support ******************/

  implicit def forHList[L <: HList](implicit folder: LeftFolder[L, Directive0, MapReduce.type]) =
    new ParamDefConverter[L, folder.Out] {
      def apply(list: L) = list.foldLeft(MiscDirectives.noop)(MapReduce)
    }

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList]
                     (implicit pdc: ParamDefConverter[T, Directive[LB]], p: PrependAux[LA, LB, Out]) =
      at[Directive[LA], T] { (a, t) => a & pdc(t) }
  }
}