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

private[spray] trait ParameterBuilders {
  this: FilterBuilders =>

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function. 
   */
  def parameter (a: Param) = parameters(a)

  /**
   * Returns a Route that rejects the request if a query parameter with the given name cannot be found.
   * If it can be found the parameters value is extracted and passed as argument to the inner Route building function.
   */
  def parameters (a: Param) = filter1[String](build(a :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters(a: Param, b: Param) = filter2[String, String](build(a :: b :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters(a: Param, b: Param, c: Param) = filter3[String, String, String](build(a :: b :: c :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters(a: Param, b: Param, c: Param, d: Param) = filter4[String, String, String, String](build(a :: b :: c :: d :: Nil))

  /**
   * Returns a Route that rejects the request if the query parameters with the given names cannot be found.
   * If it can be found the parameter values are extracted and passed as arguments to the inner Route building function.
   */
  def parameters(a: Param, b: Param, c: Param, d: Param, e: Param) = filter5[String, String, String, String, String](build(a :: b :: c :: d :: e :: Nil))
  
  private def build[T <: Product](params: List[Param]): RouteFilter[T] = { ctx =>
    params.foldLeft[FilterResult[Product]](Pass()) { (result, p) =>
      result match {
        case Pass(values, _) => p.extract(ctx.request.queryParams) match {
          case Right(value) => new Pass(values productJoin Tuple1(value), transform = identity)
          case Left(rejection) => Reject(rejection)
        }
        case x@ Reject(rejections) => p.extract(ctx.request.queryParams) match {
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
  def parameter(p: RequiredParameter) = filter { ctx =>
    ctx.request.queryParams.get(p.name) match {
      case Some(value) if value == p.requiredValue => Pass()
      case _ => Reject() 
    }
  }

  /**
   * Returns a Route that rejects the request if the query parameter with the given name cannot be found or does not
   * have the required value.
   */
  def parameters(p: RequiredParameter) = filter { ctx =>
    ctx.request.queryParams.get(p.name) match {
      case Some(value) if value == p.requiredValue => Pass()
      case _ => Reject()
    }
  }
  
  implicit def fromString(name: String): Param = new Param(name)
  implicit def fromSymbol(name: Symbol): Param = new Param(name.name)
}

class Param(val name: String, val default: Option[String] = None) {
  def ? : Param = ? ("")
  def ? (default: String) = new Param(name, Some(default))
  def ! (requiredValue: String) = new RequiredParameter(name, requiredValue)
  def extract(paramMap: Map[String, String]): Either[Rejection, String] = {
    paramMap.get(name).orElse(default).map(Right(_)).getOrElse(Left(MissingQueryParamRejection(name)))
  }
}

class RequiredParameter(val name: String, val requiredValue: String)