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

import util.DynamicVariable
import shapeless._
import cc.spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, Unmarshaller => UM, FormFieldConverter => FFC, _}
import cc.spray.http._
import directives.{FieldMatcher => FM}


trait FormFieldDirectives extends ToNameReceptaclePimps {
  import BasicDirectives._

  /**                         
   * Rejects the request if the form field with the given definition cannot be found.
   * If it can be found the field value are extracted and passed as argument to the inner Route.
   */
  def formField[T](fm: FM[T]): Directive[T :: HNil] = filter { ctx =>
    ctx.request.entity.as(fm) match {
      case Right(value) => Pass(value :: HNil)
      case Left(error) => Reject(toRejection(error, fm.fieldName))
    }
  }

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B](a: FM[A], b: FM[B]): Directive[A :: B :: HNil] =
    formField(a) & formField(b)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C](a: FM[A], b: FM[B], c: FM[C]): Directive[A :: B :: C :: HNil] =
    formFields(a, b) & formField(c)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D](a: FM[A], b: FM[B], c: FM[C], d: FM[D]): Directive[A :: B :: C :: D :: HNil] =
    formFields(a, b, c) & formField(d)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D, E](a: FM[A], b: FM[B], c: FM[C], d: FM[D],
                                e: FM[E]): Directive[A :: B :: C :: D :: E :: HNil] =
    formFields(a, b, c, d) & formField(e)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D, E, F](a: FM[A], b: FM[B], c: FM[C], d: FM[D], e: FM[E],
                                   f: FM[F]): Directive[A :: B :: C :: D :: E :: F :: HNil] =
    formFields(a, b, c, d, e) & formField(f)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D, E, F, G](a: FM[A], b: FM[B], c: FM[C], d: FM[D], e: FM[E], f: FM[F],
                                      g: FM[G]): Directive[A :: B :: C :: D :: E :: F :: G :: HNil] =
    formFields(a, b, c, d, e, f) & formField(g)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D, E, F, G, H](a: FM[A], b: FM[B], c: FM[C], d: FM[D], e: FM[E], f: FM[F], g: FM[G],
                                         h: FM[H]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: HNil] =
    formFields(a, b, c, d, e, f, g) & formField(h)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[A, B, C, D, E, F, G, H, I](a: FM[A], b: FM[B], c: FM[C], d: FM[D], e: FM[E], f: FM[F], g: FM[G], h: FM[H],
                                            i: FM[I]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: I :: HNil] =
    formFields(a, b, c, d, e, f, g, h) & formField(i)

  /**
   * Rejects the request if the form fields with the given definitions cannot be found.
   * If they can be found the field values are extracted and passed as arguments to the inner Route.
   */
  def formFields[L <: HList](fieldDefs: FieldDefs[L]): Directive[fieldDefs.Out] =
    new Directive[fieldDefs.Out] {
      def happly(inner: fieldDefs.Out => Route) = { ctx =>
        try ExtractFields.entity.withValue(ctx.request.entity) {
          inner(fieldDefs.extract)(ctx)
        } catch {
          case ExtractFields.Error(name, error) => ctx.reject(toRejection(error, name))
        }
      }
    }

  private def toRejection(error: DeserializationError, fieldName: String): Rejection = {
    error match {
      case ContentExpected => MissingFormFieldRejection(fieldName)
      case MalformedContent(msg) => MalformedFormFieldRejection(msg, fieldName)
      case UnsupportedContentType(msg) => UnsupportedRequestContentTypeRejection(msg)
    }
  }

}

object FormFieldDirectives extends FormFieldDirectives


sealed abstract class FieldMatcher[T](val fieldName: String) extends UM[T]

object FieldMatcher extends ToNameReceptaclePimps {
  implicit def fromString(s: String)(implicit a: FSOD[String], b: UM[HttpForm], c: FFC[String]) = fromNR[String](s)
  implicit def fromSymbol(s: Symbol)(implicit a: FSOD[String], b: UM[HttpForm], c: FFC[String]) = fromNR[String](s)
  implicit def fromNDesR[T](nr: NameDeserializerReceptacle[T])(implicit ev: UM[HttpForm]): FM[T] =
    fromNR(NameReceptacle[T](nr.name))(ev, FFC.fromFSOD(nr.deserializer))
  implicit def fromNDefR[T](nr: NameDefaultReceptacle[T])(implicit ev1: UM[HttpForm], ev2: FFC[T]): FM[T] =
    fromNR(NameReceptacle[T](nr.name))(ev1, ev2.withDefault(nr.default))
  implicit def fromNDesDefR[T](nr: NameDeserializerDefaultReceptacle[T])(implicit ev: UM[HttpForm]): FM[T] =
    fromNR(NameReceptacle[T](nr.name))(ev, FFC.fromFSOD(nr.deserializer.withDefaultValue(nr.default)))
  implicit def fromNR[T](nr: NameReceptacle[T])(implicit ev1: UM[HttpForm], ev2: FFC[T]): FM[T] =
    new FM[T](nr.name) {
      def apply(entity: HttpEntity): Deserialized[T] = entity.as[HttpForm].right.flatMap(_.field(fieldName).as[T])
    }
}


sealed trait FieldDefs[L <: HList] {
  type Out <: HList
  def extract: Out
}
object FieldDefs {
  implicit def fromDefs[L <: HList](defs: L)(implicit mapper: Mapper[ExtractFields.type, L]) =
    new FieldDefs[L] {
      type Out = mapper.Out
      def extract = defs.map(ExtractFields)
    }
}


private[directives] object ExtractFields extends Poly1 {
  val entity = new DynamicVariable[HttpEntity](null)

  implicit def from[A, B](implicit fmFor: A => FM[B]) = at[A] { fieldDef =>
    val fm = fmFor(fieldDef)
    entity.value.as(fm) match {
      case Right(value) => value
      case Left(error) => throw Error(fm.fieldName, error)
    }
  }

  case class Error(paramName: String, error: DeserializationError) extends RuntimeException
}