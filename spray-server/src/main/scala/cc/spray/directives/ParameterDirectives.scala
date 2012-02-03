/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package directives

import typeconversion._
import java.lang.IllegalStateException

private[spray] trait ParameterDirectives {
  this: BasicDirectives =>

  private type PM[A] = ParameterMatcher[A]
  
  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function. 
   */
  def parameter[A](pm: PM[A]): SprayRoute1[A] = filter1[A] { ctx =>
    val ParamName = pm.name
    pm(ctx.request.queryParams) match {
      case Right(value) => Pass.withTransform(value) {
        _.cancelRejections {
          _ match {
            case MissingQueryParamRejection(ParamName) => true
            case MalformedQueryParamRejection(_, ParamName) => true
            case _ => false
          }
        }
      }
      case Left(ContentExpected) => Reject(MissingQueryParamRejection(ParamName))
      case Left(MalformedContent(errorMsg)) => Reject(MalformedQueryParamRejection(errorMsg, ParamName))
      case Left(UnsupportedContentType(_)) => throw new IllegalStateException
    }
  }

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function.
   */
  def parameters[A](a: PM[A]): SprayRoute1[A] = parameter(a)

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B](a: PM[A], b: PM[B]): SprayRoute2[A, B] = {
    parameter(a) & parameter(b)
  }  

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C](a: PM[A], b: PM[B], c: PM[C]): SprayRoute3[A, B, C] = {
    parameters(a, b) & parameter(c)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D](a: PM[A], b: PM[B], c: PM[C], d: PM[D]): SprayRoute4[A, B, C, D] = {
    parameters(a, b, c) & parameter(d)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E]): SprayRoute5[A, B, C, D, E] = {
    parameters(a, b, c, d) & parameter(e)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                   f: PM[F]): SprayRoute6[A, B, C, D, E, F] = {
    parameters(a, b, c, d, e) & parameter(f)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F, G](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                      f: PM[F], g: PM[G]): SprayRoute7[A, B, C, D, E, F, G] = {
    parameters(a, b, c, d, e, f) & parameter(g)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F, G, H](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                         f: PM[F], g: PM[G], h: PM[H]): SprayRoute8[A, B, C, D, E, F, G, H] = {
    parameters(a, b, c, d, e, f, g) & parameter(h)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F, G, H, I](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                            f: PM[F], g: PM[G], h: PM[H], i: PM[I]): SprayRoute9[A, B, C, D, E, F, G, H, I] = {
    parameters(a, b, c, d, e, f, g, h) & parameter(i)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameter(p: RequiredParameterMatcher) = filter { ctx => if (p(ctx.request.queryParams)) Pass else Reject() }

  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameters(p: RequiredParameterMatcher, more: RequiredParameterMatcher*) = {
    val allRPM = p +: more
    filter { ctx => if (allRPM.forall(_(ctx.request.queryParams))) Pass else Reject() }
  }

  implicit def symbol2PM(name: Symbol)(implicit ev: FromStringOptionDeserializer[String]) =
    new ParameterMatcher[String](name.name)
  implicit def string2PM(name: String)(implicit ev: FromStringOptionDeserializer[String]) =
    new ParameterMatcher[String](name)
  implicit def nameReceptacle2PM[A :FromStringOptionDeserializer](r: NameReceptacle[A]) =
    new ParameterMatcher[A](r.name)
  implicit def nameDeserializerReceptacle2PM[A](r: NameDeserializerReceptacle[A]) =
    new ParameterMatcher[A](r.name)(r.deserializer)
  implicit def nameDefaultReceptacle2PM[A :FromStringOptionDeserializer](r: NameDefaultReceptacle[A]) =
    new ParameterMatcher[A](r.name)(withDefaultDeserializer(r.default))
  implicit def nameDeserializerDefaultReceptacle2PM[A](r: NameDeserializerDefaultReceptacle[A]) =
    new ParameterMatcher[A](r.name)(withDefaultDeserializer(r.default)(r.deserializer))
  implicit def requiredValueReceptacle2RPM[A :FromStringOptionDeserializer]
    (r: RequiredValueReceptacle[A]): RequiredParameterMatcher = { paramsMap =>
    fromStringOptionDeserializer[A].apply(paramsMap.get(r.name)) == Right(r.requiredValue)
  }
  implicit def requiredValueDeserializerReceptacle2RPM[A]
    (r: RequiredValueDeserializerReceptacle[A]): RequiredParameterMatcher = { paramsMap =>
    r.deserializer(paramsMap.get(r.name)) == Right(r.requiredValue)
  }

  private def withDefaultDeserializer[A :FromStringOptionDeserializer](default: A): FromStringOptionDeserializer[A] = {
    new FromStringOptionDeserializer[A] {
      def apply(s: Option[String]) = fromStringOptionDeserializer[A].apply(s).left.flatMap {
        case ContentExpected => Right(default)
        case error => Left(error)
      }
    }
  }
}

class ParameterMatcher[A: FromStringOptionDeserializer](val name: String) extends Deserializer[Map[String, String], A] {
  def apply(params: Map[String, String]) = fromStringOptionDeserializer[A].apply(params.get(name))
}