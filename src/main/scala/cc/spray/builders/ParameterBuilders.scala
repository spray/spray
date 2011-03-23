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
  
  def parameter (a: Param) = filter1(build(a :: Nil))
  def parameters(a: Param, b: Param) = filter2(build(b :: a :: Nil))
  def parameters(a: Param, b: Param, c: Param) = filter3(build(c :: b :: a :: Nil))
  def parameters(a: Param, b: Param, c: Param, d: Param) = filter4(build(d :: c :: b :: a :: Nil))
  def parameters(a: Param, b: Param, c: Param, d: Param, e: Param) = filter5(build(e :: d :: c :: b :: a :: Nil))
  
  private def build(params: List[Param]): RouteFilter[String] = { ctx =>
    params.foldLeft[FilterResult[String]](Pass()) { (result, p) =>
      result match {
        case Pass(values, _) => p.extract(ctx.request.queryParams) match {
          case Right(value) => Pass(value :: values)
          case Left(rejection) => Reject(rejection)
        }
        case x@ Reject(rejections) => p.extract(ctx.request.queryParams) match {
          case Left(rejection) => Reject(rejections + rejection)
          case _ => x
        }
      }
    }
  }
  
  def parameter(p: RequiredParameter) = filter { ctx =>
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