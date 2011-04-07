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
package builders

import utils.Product0

private[spray] trait ParameterBuilders {
  this: FilterBuilders =>

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function. 
   */
  def parameter[A](a: ParameterMatcher[A]) = parameters(a)

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function.
   */
  def parameters[A](a: ParameterMatcher[A]) = filter1[A](build(a :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B](a: ParameterMatcher[A], b: ParameterMatcher[B]) = filter2[A, B](build(a :: b :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C](a: ParameterMatcher[A], b: ParameterMatcher[B], c: ParameterMatcher[C]) = {
    filter3[A, B, C](build(a :: b :: c :: Nil))
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D](a: ParameterMatcher[A], b: ParameterMatcher[B], c: ParameterMatcher[C],
                             d: ParameterMatcher[D]) = {
    filter4[A, B, C, D](build(a :: b :: c :: d :: Nil))
  }

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters[A, B, C, D, E](a: ParameterMatcher[A], b: ParameterMatcher[B], c: ParameterMatcher[C],
                                d: ParameterMatcher[D], e: ParameterMatcher[E]) = {
    filter5[A, B, C, D, E](build(a :: b :: c :: d :: e :: Nil))
  }

  private def build[T <: Product](params: List[ParameterMatcher[_]]): RouteFilter[T] = { ctx =>
    params.foldLeft[FilterResult[Product]](Pass()) { (result, p) =>
      result match {
        case Pass(values, _) => p(ctx.request.queryParams) match {
          case Right(value) => new Pass(values productJoin Tuple1(value), transform = identity)
          case Left(rejection) => Reject(rejection)
        }
        case x@ Reject(rejections) => p(ctx.request.queryParams) match {
          case Left(rejection) => Reject(rejections + rejection)
          case _ => x
        }
      }
    }.asInstanceOf[FilterResult[T]]
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
    def apply(string: String) = Some(string)
  } 
}

trait ParameterConverter[A] extends (String => Option[A])
trait RequiredParameterMatcher extends (Map[String, String] => Boolean)

class DefaultParameterMatcher[A](name: String, converter: ParameterConverter[A]) extends ParameterMatcher[A] { self =>
  def apply(params: Map[String, String]) = {
    params.get(name) match {
      case Some(value) => converter(value) match {
        case Some(converted) => Right(converted)
        case None => Left(MalformedQueryParamRejection(name)) 
      }
      case None => notFound  
    }
  }
  
  def notFound: Either[Rejection, A] = Left(MissingQueryParamRejection(name))
  
  def ? : ParameterMatcher[Option[A]] = {
    new DefaultParameterMatcher[Option[A]](name,
      new ParameterConverter[Option[A]] { def apply(s: String) = converter(s).map(Some(_)) }) {
      override def notFound = Right(None)
    }
  }
  
  def ? [B](default: B)(implicit converter: ParameterConverter[B]) = new DefaultParameterMatcher[B](name, converter) {
    override def notFound = Right(default)
  }
  
  def as[B](implicit converter: ParameterConverter[B]) = new DefaultParameterMatcher[B](name, converter)
  
  def ! [B](requiredValue: B)(implicit converter: ParameterConverter[B]) = new RequiredParameterMatcher {
    def apply(params: Map[String, String]) = params.get(name).flatMap(converter) == Some(requiredValue)
  } 
}