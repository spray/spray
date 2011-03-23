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

private[spray] trait FilterBuilders {
  
  def filter [A](filter: RouteFilter[A]) = new FilterRoute0[A](filter)
  def filter1[A](filter: RouteFilter[A]) = new FilterRoute1[A](filter)
  def filter2[A](filter: RouteFilter[A]) = new FilterRoute2[A](filter)
  def filter3[A](filter: RouteFilter[A]) = new FilterRoute3[A](filter)
  def filter4[A](filter: RouteFilter[A]) = new FilterRoute4[A](filter)
  def filter5[A](filter: RouteFilter[A]) = new FilterRoute5[A](filter)
}

abstract class FilterRoute[A](val filter: RouteFilter[A]) { self =>
  protected def fromRouting(f: List[A] => Route): Route = { ctx =>
    filter(ctx) match {
      case Pass(values, transform) => f(values)(transform(ctx)) 
      case Reject(rejections) => ctx.reject(rejections)
    }
  }
  protected def chainFilterWith(other: RouteFilter[A]): RouteFilter[A] = { ctx =>
    self.filter(ctx) match {
      case x: Pass[_] => x
      case Reject(rejections1) => other(ctx) match {
        case x: Pass[_] => x
        case Reject(rejections2) => Reject(rejections1 ++ rejections2) 
      }
    }
  } 
}

class FilterRoute0[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with (Route => Route) {
  def apply(route: Route) = fromRouting(_ => route) 
  def | (other: FilterRoute0[A]) = new FilterRoute0[A](chainFilterWith(other.filter))
}

class FilterRoute1[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with ((A => Route) => Route) {
  def apply(routing: A => Route) = fromRouting {
    case a :: Nil => routing(a)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute1[A]) = new FilterRoute1[A](chainFilterWith(other.filter))
}

class FilterRoute2[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with (((A, A) => Route) => Route) {
  def apply(routing: (A, A) => Route) = fromRouting {
    case a :: b :: Nil => routing(a, b)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute2[A]) = new FilterRoute2[A](chainFilterWith(other.filter))
}

class FilterRoute3[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with (((A, A, A) => Route) => Route) {
  def apply(routing: (A, A, A) => Route) = fromRouting {
    case a :: b :: c :: Nil => routing(a, b, c)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute3[A]) = new FilterRoute3[A](chainFilterWith(other.filter))
}

class FilterRoute4[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with (((A, A, A, A) => Route) => Route) {
  def apply(routing: (A, A, A, A) => Route) = fromRouting {
    case a :: b :: c :: d :: Nil => routing(a, b, c, d)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute4[A]) = new FilterRoute4[A](chainFilterWith(other.filter))
}

class FilterRoute5[A](filter: RouteFilter[A]) extends FilterRoute[A](filter) with (((A, A, A, A, A) => Route) => Route) {
  def apply(routing: (A, A, A, A, A) => Route) = fromRouting {
    case a :: b :: c :: d :: e :: Nil => routing(a, b, c, d, e)
    case _ => throw new IllegalStateException
  }
  def | (other: FilterRoute5[A]) = new FilterRoute5[A](chainFilterWith(other.filter))
}
