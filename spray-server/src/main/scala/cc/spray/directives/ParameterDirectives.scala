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

private[spray] trait ParameterDirectives {
  this: BasicDirectives =>

  private type PM[A] = ParameterMatcher[A]
  
  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function. 
   */
  def parameter[A](pm: PM[A]): SprayRoute1[A] = filter1[A] { ctx =>
    pm(ctx.request.queryParams) match {
      case Right(value) => Pass.withTransform(value) {
        _.cancelRejections {
          _ match {
            case MissingQueryParamRejection(n) if n == pm.name => true
            case MalformedQueryParamRejection(_, Some(n)) if n == pm.name => true
            case _ => false
          }
        }
      }
      case Left(x: MissingQueryParamRejection) => Reject(x)
      case Left(x) => new Reject(Set(x,
        RejectionRejection {
          case MissingQueryParamRejection(n) if n == pm.name => true
          case _ => false
        }
      ))
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
  def parameter(p: RequiredParameterMatcher) = filter { ctx => if (p(ctx.request.queryParams)) Pass() else Reject() }

  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameters(p: RequiredParameterMatcher, more: RequiredParameterMatcher*) = {
    val allRPM = p +: more
    filter { ctx => if (allRPM.forall(_(ctx.request.queryParams))) Pass() else Reject() }
  }

  implicit def fromSymbol(name: Symbol): ParameterMatcher[String] = fromString(name.name)
  
  implicit def fromString(name: String): ParameterMatcher[String] = new ParameterMatcher(name)
}

class ParameterMatcher[A :FromStringConverter](val name: String) {
  def apply(params: Map[String, String]): Either[Rejection, A] = {
    params.get(name) match {
      case Some(value) => fromStringConverter[A].apply(value).left.map(MalformedQueryParamRejection(_, Some(name)))
      case None => notFound
    }
  }

  protected def notFound: Either[Rejection, A] = Left(MissingQueryParamRejection(name))
  
  def ? = {
    implicit val toOptionLifter = new FromStringConverter[Option[A]] {
      def apply(s: String) = fromStringConverter[A].apply(s).right.map(Some(_))
    }
    new ParameterMatcher[Option[A]](name) {
      override def notFound = Right(None)
    }
  }
  
  def ? [B :FromStringConverter](default: B) = new ParameterMatcher[B](name) {
    override def notFound = Right(default)
  }
  
  def as[B :FromStringConverter] = new ParameterMatcher[B](name)
  
  def ! [B :FromStringConverter](requiredValue: B): RequiredParameterMatcher = {
    _.get(name).flatMap(fromStringConverter[B].apply(_).right.toOption) == Some(requiredValue)
  }
}