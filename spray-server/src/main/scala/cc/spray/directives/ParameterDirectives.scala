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

private[spray] trait ParameterDirectives extends ParameterConverters {
  this: BasicDirectives =>

  private type PM[A] = ParameterMatcher[A]
  
  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function. 
   */
  def parameter[A](pm: PM[A]): FilterRoute1[A] = filter1[A] { ctx => pm(ctx.request.queryParams) }

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function.
   */
  def parameters[A](a: PM[A]): FilterRoute1[A] = parameter(a)

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B](a: PM[A], b: PM[B]): FilterRoute2[A, B] = {
    parameter(a) & parameter(b)
  }  

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C](a: PM[A], b: PM[B], c: PM[C]): FilterRoute3[A, B, C] = {
    parameters(a, b) & parameter(c)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D](a: PM[A], b: PM[B], c: PM[C], d: PM[D]): FilterRoute4[A, B, C, D] = {
    parameters(a, b, c) & parameter(d)
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E]): FilterRoute5[A, B, C, D, E] = {
    parameters(a, b, c, d) & parameter(e)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                   f: PM[F]): FilterRoute6[A, B, C, D, E, F] = {
    parameters(a, b, c, d, e) & parameter(f)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E, F, G](a: PM[A], b: PM[B], c: PM[C], d: PM[D], e: PM[E],
                                      f: PM[F], g: PM[G]): FilterRoute7[A, B, C, D, E, F, G] = {
    parameters(a, b, c, d, e, f) & parameter(g)
  }
  
  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameter(p: RequiredParameterMatcher) = filter { ctx => if (p(ctx.request.queryParams)) Pass() else Reject() }

  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameters(p: RequiredParameterMatcher, more: RequiredParameterMatcher*) = {
    val allRPM = p +: more
    filter { ctx => if (allRPM.forall(_(ctx.request.queryParams))) Pass() else Reject() }
  }

  implicit def fromSymbol(name: Symbol) = fromString(name.name)  
  
  implicit def fromString(name: String) = new DefaultParameterMatcher[String](name, StringParameterConverter)
  
  implicit object StringParameterConverter extends ParameterConverter[String] {
    def apply(string: String) = Right(string)
  } 
}

trait ParameterConverter[A] extends (String => Either[String, A])
trait RequiredParameterMatcher extends (Map[String, String] => Boolean)

class DefaultParameterMatcher[A](name: String, converter: ParameterConverter[A]) extends ParameterMatcher[A] { self =>
  def apply(params: Map[String, String]) = {
    params.get(name) match {
      case Some(value) => converter(value) match {
        case Right(converted) => Pass(converted) {
          _.cancelRejections {
            _ match {
              case MissingQueryParamRejection(n) if n == name => true
              case MalformedQueryParamRejection(n, _) if n == name => true
              case _ => false
            }
          }
        }
        case Left(errorMsg) => new Reject(Set(
          MalformedQueryParamRejection(name, errorMsg),
          RejectionRejection {
            case MissingQueryParamRejection(n) if n == name => true
            case _ => false
          }
        )) 
      }
      case None => notFound  
    }
  }
  
  protected def notFound: FilterResult[Tuple1[A]] = Reject(MissingQueryParamRejection(name))
  
  def ? : ParameterMatcher[Option[A]] = {
    new DefaultParameterMatcher[Option[A]](name,
      new ParameterConverter[Option[A]] { def apply(s: String) = converter(s).fold(Left(_), x => Right(Some(x))) }) {
      override def notFound = Pass(None)
    }
  }
  
  def ? [B](default: B)(implicit converter: ParameterConverter[B]) = new DefaultParameterMatcher[B](name, converter) {
    override def notFound = Pass(default)
  }
  
  def as[B](implicit converter: ParameterConverter[B]) = new DefaultParameterMatcher[B](name, converter)
  
  def ! [B](requiredValue: B)(implicit converter: ParameterConverter[B]) = new RequiredParameterMatcher {
    def apply(params: Map[String, String]) = {
      params.get(name).flatMap(converter(_).right.toOption) == Some(requiredValue)
    }
  } 
}