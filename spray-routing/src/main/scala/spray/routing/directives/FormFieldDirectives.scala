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
import spray.http._

trait FormFieldDirectives extends ToNameReceptaclePimps {

  /**
   * Rejects the request if the form field parameter matcher(s) defined by the definition(s) don't match.
   * Otherwise the field content(s) are extracted and passed to the inner route.
   */
  /* directive */ def formField(fdm: FieldDefMagnet): fdm.Out = fdm()

  /**
   * Rejects the request if the form field parameter matcher(s) defined by the definition(s) don't match.
   * Otherwise the field content(s) are extracted and passed to the inner route.
   */
  /* directive */ def formFields(fdm: FieldDefMagnet): fdm.Out = fdm()

}

object FormFieldDirectives extends FormFieldDirectives

trait FieldDefMagnet {
  type Out
  def apply(): Out
}
object FieldDefMagnet {
  implicit def apply[T](value: T)(implicit fdm2: FieldDefMagnet2[T]) = new FieldDefMagnet {
    type Out = fdm2.Out
    def apply() = fdm2(value)
  }
}

trait FieldDefMagnet2[T] {
  type Out
  def apply(value: T): Out
}

object FieldDefMagnet2 extends ToNameReceptaclePimps {
  type FieldDefMagnetAux[A, B] = FieldDefMagnet2[A] { type Out = B }
  def FieldDefMagnetAux[A, B](f: A ⇒ B) = new FieldDefMagnet2[A] { type Out = B; def apply(value: A) = f(value) }

  import spray.httpx.unmarshalling.{ FromRequestUnmarshaller ⇒ UM, FormFieldConverter ⇒ FFC, FromBodyPartOptionUnmarshaller ⇒ FBPOU, _ }
  import BasicDirectives._
  import RouteDirectives._

  /************ "regular" field extraction ******************/

  def extractField[A, B](f: A ⇒ Directive1[B]) = FieldDefMagnetAux[A, Directive1[B]](f)

  private def filter[A, B](nr: NameReceptacle[A])(implicit ev1: UM[HttpForm], ev2: FFC[B]): Directive1[B] =
    extract(_.request.as[HttpForm].right.flatMap(_.field(nr.name).as[B])).flatMap {
      case Right(value)                       ⇒ provide(value)
      case Left(ContentExpected)              ⇒ reject(MissingFormFieldRejection(nr.name))
      case Left(MalformedContent(msg, cause)) ⇒ reject(MalformedFormFieldRejection(nr.name, msg, cause))
      case Left(UnsupportedContentType(msg))  ⇒ reject(UnsupportedRequestContentTypeRejection(msg))
    }
  implicit def forString(implicit ev1: UM[HttpForm], ev2: FFC[String]) =
    extractField[String, String](string ⇒ filter(string))
  implicit def forSymbol(implicit ev1: UM[HttpForm], ev2: FFC[String]) =
    extractField[Symbol, String](symbol ⇒ filter(symbol))
  implicit def forNDesR[T](implicit ev1: UM[HttpForm], ev2: FBPOU[T] = null) =
    extractField[NameDeserializerReceptacle[T], T] { ndr ⇒
      filter(NameReceptacle[T](ndr.name))(ev1, FFC.fromFSOD(ndr.deserializer))
    }
  implicit def forNDefR[T](implicit ev1: UM[HttpForm], ev2: FFC[T]) =
    extractField[NameDefaultReceptacle[T], T] { ndr ⇒
      filter(NameReceptacle[T](ndr.name))(ev1, ev2.withDefault(ndr.default))
    }
  implicit def forNDesDefR[T](implicit ev1: UM[HttpForm], ev2: FBPOU[T] = null) =
    extractField[NameDeserializerDefaultReceptacle[T], T] { ndr ⇒
      filter(NameReceptacle[T](ndr.name))(ev1, FFC.fromFSOD(ndr.deserializer.withDefaultValue(ndr.default)))
    }
  implicit def forNR[T](implicit ev1: UM[HttpForm], ev2: FFC[T]) =
    extractField[NameReceptacle[T], T](nr ⇒ filter(nr))

  /************ required formField support ******************/

  private def requiredFilter[A](paramName: String, requiredValue: A)(implicit ev1: UM[HttpForm], ffc: FFC[A]) =
    filter(NameReceptacle[A](paramName))
      .require(_ == requiredValue, MalformedFormFieldRejection(paramName, s"Form field '$paramName' had wrong value."))
  implicit def forRVR[T](implicit ev1: UM[HttpForm], ev2: FFC[T]) =
    FieldDefMagnetAux[RequiredValueReceptacle[T], Directive0] { rvr ⇒
      requiredFilter(rvr.name, rvr.requiredValue)
    }
  implicit def forRVDR[T](implicit ev1: UM[HttpForm], ev2: FBPOU[T] = null) =
    FieldDefMagnetAux[RequiredValueDeserializerReceptacle[T], Directive0] { rvr ⇒
      requiredFilter(rvr.name, rvr.requiredValue)(ev1, FFC.fromFSOD(rvr.deserializer))
    }

  /************ tuple support ******************/

  implicit def forTuple[T <: Product, L <: HList, Out0](implicit hla: HListerAux[T, L], fdma: FieldDefMagnetAux[L, Out0]) =
    FieldDefMagnetAux[T, Out0](tuple ⇒ fdma(hla(tuple)))

  /************ HList support ******************/

  implicit def forHList[L <: HList](implicit f: LeftFolder[L, Directive0, MapReduce.type]) =
    FieldDefMagnetAux[L, f.Out](_.foldLeft(BasicDirectives.noop)(MapReduce))

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList](implicit fdma: FieldDefMagnetAux[T, Directive[LB]], ev: PrependAux[LA, LB, Out]) =
      at[Directive[LA], T] { (a, t) ⇒ a & fdma(t) }
  }
}
