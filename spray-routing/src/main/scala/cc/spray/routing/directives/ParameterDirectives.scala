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
import util.DynamicVariable
import shapeless._
import cc.spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, _}
import cc.spray.http.QueryParams
import directives.{ParameterMatcher => PM}


trait ParameterDirectives extends ToNameReceptaclePimps {
  import BasicDirectives._

  /**
   * Rejects the request if the query parameter with the given definition cannot be found.
   * If it can be found the parameter value are extracted and passed as argument to the inner Route.
   */
  def parameter[T](pm: PM[T]): Directive[T :: HNil] = filter { ctx =>
    pm.deserializer(ctx.request.queryParams.get(pm.paramName)) match {
      case Right(value) => Pass(value :: HNil)
      case Left(error) => Reject(toRejection(error, pm.paramName))
    }
  }
  
  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B](a: PM[A], b: PM[B]): Directive[A :: B :: HNil] =
    parameter(a) & parameter(b)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C](a: PM[A], b: PM[B], c: PM[C]): Directive[A :: B :: C :: HNil] =
    parameters(a, b) & parameter(c)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D](a: PM[A], b: PM[B], c: PM[C], d: PM[D]): Directive[A :: B :: C :: D :: HNil] =
    parameters(a, b, c) & parameter(d)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E](a: PM[A], b: PM[B], c: PM[C], d: PM[D],
                                e: PM[E]): Directive[A :: B :: C :: D :: E :: HNil] =
    parameters(a, b, c, d) & parameter(e)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                   f: PM[F]): Directive[A :: B :: C :: D :: E :: F :: HNil] =
    parameters(a, b, c, d, e) & parameter(f)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E], f: PM[F],
                                      g: PM[G]): Directive[A :: B :: C :: D :: E :: F :: G :: HNil] =
    parameters(a, b, c, d, e, f) & parameter(g)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G, H](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E], f: PM[F], g: PM[G],
                                         h: PM[H]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: HNil] =
    parameters(a, b, c, d, e, f, g) & parameter(h)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G, H, I](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E], f: PM[F], g: PM[G], h: PM[H],
                                            i: PM[I]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: I :: HNil] =
    parameters(a, b, c, d, e, f, g, h) & parameter(i)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[L <: HList](paramDefs: ParamDefs[L]): Directive[paramDefs.Out] =
    new Directive[paramDefs.Out] {
      def happly(inner: paramDefs.Out => Route) = { ctx =>
        try ExtractParams.queryParams.withValue(ctx.request.queryParams) {
          inner(paramDefs.extract)(ctx)
        } catch {
          case ExtractParams.Error(name, error) => ctx.reject(toRejection(error, name))
        }
      }
    }

  private def toRejection(error: DeserializationError, paramName: String): Rejection = {
    error match {
      case ContentExpected => MissingQueryParamRejection(paramName)
      case MalformedContent(errorMsg) => MalformedQueryParamRejection(errorMsg, paramName)
      case x: UnsupportedContentType => throw new IllegalStateException(x.toString)
    }
  }

  /**
   * Extracts the requests query parameters as a Map[String, String].
   */
  def parameterMap: Directive[QueryParams :: HNil] =
    filter { ctx => Pass(ctx.request.queryParams :: HNil) }

  /**
   * Rejects the request if the query parameter with the given name cannot be found or does not have the required value.
   */
  def parameter(pm: RequiredParameterMatcher): Directive0 = filter { ctx =>
    pm.deserializer(ctx.request.queryParams.get(pm.paramName)) match {
      case Right(value) if value == pm.requiredValue => Pass.Empty
      case _ => Reject.Empty 
    }
  }

  /**
   * Rejects the request if the query parameters with the given names cannot be found or do not have the required values.
   */
  def parameters(rvr: RequiredParameterMatcher, more: RequiredParameterMatcher*): Directive0 =
    (rvr +: more).map(parameter(_)).reduceLeft(_ & _)
}

object ParameterDirectives extends ParameterDirectives


case class ParameterMatcher[T](paramName: String, deserializer: FSOD[T])
object ParameterMatcher extends ToNameReceptaclePimps {
  implicit def fromString(s: String)(implicit fsod: FSOD[String]) = PM[String](s, fsod)
  implicit def fromSymbol(s: Symbol)(implicit fsod: FSOD[String]) = PM[String](s.name, fsod)
  implicit def fromNDesR[T](nr: NameDeserializerReceptacle[T]) = PM[T](nr.name, nr.deserializer)
  implicit def fromNDefR[T](nr: NameDefaultReceptacle[T])(implicit fsod: FSOD[T]) =
    PM[T](nr.name, fsod.withDefaultValue(nr.default))
  implicit def fromNDesDefR[T](nr: NameDeserializerDefaultReceptacle[T]) =
    PM[T](nr.name, nr.deserializer.withDefaultValue(nr.default))
  implicit def fromNR[T](nr: NameReceptacle[T])(implicit fsod: FSOD[T]) = PM[T](nr.name, fsod)
}

case class RequiredParameterMatcher(paramName: String, deserializer: FSOD[_], requiredValue: Any)
object RequiredParameterMatcher {
  implicit def fromRVR[T](rvr: RequiredValueReceptacle[T])(implicit fsod: FSOD[T]) =
    RequiredParameterMatcher(rvr.name, fsod, rvr.requiredValue)
  implicit def fromRVDR[T](rvr: RequiredValueDeserializerReceptacle[T]) =
    RequiredParameterMatcher(rvr.name, rvr.deserializer, rvr.requiredValue)
}


sealed trait ParamDefs[L <: HList] {
  type Out <: HList
  def extract: Out
}
object ParamDefs {
  implicit def fromDefs[L <: HList](defs: L)(implicit mapper: Mapper[ExtractParams.type, L]) =
    new ParamDefs[L] {
      type Out = mapper.Out
      def extract = defs.map(ExtractParams)
    }
}


private[directives] object ExtractParams extends Poly1 {
  val queryParams = new DynamicVariable[QueryParams](null)

  implicit def from[A, B](implicit pmFor: A => PM[B]) = at[A] { paramDef =>
    val pm = pmFor(paramDef)
    pm.deserializer(queryParams.value.get(pm.paramName)) match {
      case Right(value) => value
      case Left(error) => throw Error(pm.paramName, error)
    }
  }

  case class Error(paramName: String, error: DeserializationError) extends RuntimeException
}