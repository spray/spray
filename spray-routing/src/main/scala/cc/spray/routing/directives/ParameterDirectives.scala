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
import cc.spray.httpx.unmarshalling._
import cc.spray.http.QueryParams
import shapeless._
import util.DynamicVariable


trait ParameterDirectives extends ToNameReceptaclePimps {
  import BasicDirectives._

  private type NR[T] = NameReceptacle[T]

  /**
   * Rejects the request if the query parameter with the given definition cannot be found.
   * If it can be found the parameter value are extracted and passed as argument to the inner Route.
   */
  def parameter[T](nr: NR[T]): Directive[T :: HNil] = filter { ctx =>
    nr.deserializer(ctx.request.queryParams.get(nr.name)) match {
      case Right(value) => Pass(value :: HNil)
      case Left(error) => Reject(toRejection(error, nr.name))
    }
  }
  
  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B](a: NR[A], b: NR[B]): Directive[A :: B :: HNil] =
    parameter(a) & parameter(b)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C](a: NR[A], b: NR[B], c: NR[C]): Directive[A :: B :: C :: HNil] =
    parameters(a, b) & parameter(c)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D](a: NR[A], b: NR[B], c: NR[C], d: NR[D]): Directive[A :: B :: C :: D :: HNil] =
    parameters(a, b, c) & parameter(d)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E](a: NR[A], b: NR[B], c: NR[C], d: NR[D],
                                e: NR[E]): Directive[A :: B :: C :: D :: E :: HNil] =
    parameters(a, b, c, d) & parameter(e)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F](a: NR[A], b: NR[B], c: NR[C], d: NR[D], e: NR[E],
                                   f: NR[F]): Directive[A :: B :: C :: D :: E :: F :: HNil] =
    parameters(a, b, c, d, e) & parameter(f)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G](a: NR[A], b: NR[B], c: NR[C], d: NR[D], e: NR[E], f: NR[F],
                                      g: NR[G]): Directive[A :: B :: C :: D :: E :: F :: G :: HNil] =
    parameters(a, b, c, d, e, f) & parameter(g)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G, H](a: NR[A], b: NR[B], c: NR[C], d: NR[D], e: NR[E], f: NR[F], g: NR[G],
                                         h: NR[H]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: HNil] =
    parameters(a, b, c, d, e, f, g) & parameter(h)

  /**
   * Rejects the request if the query parameters with the given definitions cannot be found.
   * If they can be found the parameter values are extracted and passed as arguments to the inner Route.
   */
  def parameters[A, B, C, D, E, F, G, H, I](a: NR[A], b: NR[B], c: NR[C], d: NR[D], e: NR[E], f: NR[F], g: NR[G], h: NR[H],
                                            i: NR[I]): Directive[A :: B :: C :: D :: E :: F :: G :: H :: I :: HNil] =
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
  def parameter(rvr: RequiredValueReceptacle[_]): Directive0 = filter { ctx =>
    rvr.deserializer(ctx.request.queryParams.get(rvr.name)) match {
      case Right(value) if value == rvr.requiredValue => Pass.Empty
      case _ => Reject.Empty
    }
  }

  /**
   * Rejects the request if the query parameters with the given names cannot be found or do not have the required values.
   */
  def parameters(rvr: RequiredValueReceptacle[_], more: RequiredValueReceptacle[_]*): Directive0 =
    (rvr +: more).map(parameter(_)).reduceLeft(_ & _)

}

object ParameterDirectives extends ParameterDirectives


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

  private def extract[T](name: String)(implicit fsod: FromStringOptionDeserializer[T]): T = {
    fsod(queryParams.value.get(name)) match {
      case Right(value) => value
      case Left(error) => throw Error(name, error)
    }
  }
  implicit def fromSymbol = at[Symbol] { symbol => extract[String](symbol.name) }
  implicit def fromString = at[String] { string => extract[String](string) }
  implicit def fromNameReceptacle[T] = at[NameReceptacle[T]] { nr => extract[T](nr.name)(nr.deserializer) }

  case class Error(paramName: String, error: DeserializationError) extends RuntimeException
}