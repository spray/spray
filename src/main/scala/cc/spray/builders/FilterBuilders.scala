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

private[spray] trait FilterBuilders {

  /**
   * Creates a [[cc.spray.FilterRoute0]] from the given RouteFilter function. 
   */
  def filter(filter: RouteFilter[Product0]) = new FilterRoute0(filter)
  
  /**
   * Created a [[cc.spray.FilterRoute1]] from the given RouteFilter function. 
   */
  def filter1[A](filter: RouteFilter[Tuple1[A]]) = new FilterRoute1(filter)
  
  /**
   * Creates a [[cc.spray.FilterRoute2]] from the given RouteFilter function. 
   */
  def filter2[A, B](filter: RouteFilter[(A, B)]) = new FilterRoute2(filter)
  
  /**
   * Creates a [[cc.spray.FilterRoute3]] from the given RouteFilter function. 
   */
  def filter3[A, B, C](filter: RouteFilter[(A, B, C)]) = new FilterRoute3(filter)
  
  /**
   * Creates a [[cc.spray.FilterRoute4]] from the given RouteFilter function. 
   */
  def filter4[A, B, C, D](filter: RouteFilter[(A, B, C, D)]) = new FilterRoute4(filter)
  
  /**
   * Creates a [[cc.spray.FilterRoute5]] from the given RouteFilter function. 
   */
  def filter5[A, B, C, D, E](filter: RouteFilter[(A, B, C, D, E)]) = new FilterRoute5(filter)
}

abstract class FilterRoute[T <: Product](val filter: RouteFilter[T]) { self =>
  protected def fromRouting(f: T => Route): Route = { ctx =>
    filter(ctx) match {
      case Pass(values, transform) => f(values)(transform(ctx)) 
      case Reject(rejections) => ctx.reject(rejections)
    }
  }
  protected def or(other: RouteFilter[T]): RouteFilter[T] = { ctx =>
    self.filter(ctx) match {
      case x: Pass[_] => x
      case Reject(rejections1) => other(ctx) match {
        case x: Pass[_] => x
        case Reject(rejections2) => Reject(rejections1 ++ rejections2) 
      }
    }
  } 
  protected def and[S <: Product, R <: Product](other: RouteFilter[S]): RouteFilter[R] = { ctx =>
    self.filter(ctx) match {
      case Pass(values1, transform1) => other(transform1(ctx)) match {
        case Pass(values2, transform2) => {
          new Pass((values1 productJoin values2).asInstanceOf[R], transform1 andThen transform2)          
        }
        case x: Reject => x
      }
      case x: Reject => x
    }
  }
}

/**
 * A Route using the given RouteFilter function on all inner Routes it is applied to.
 */
class FilterRoute0(filter: RouteFilter[Product0]) extends FilterRoute(filter) with (Route => Route) {  
  def apply(route: Route) = fromRouting { _ => route }
  def | (other: FilterRoute0) = new FilterRoute0(or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute0(and(other.filter))
  def & [A](other: FilterRoute1[A]) = new FilterRoute1[A](and(other.filter))
  def & [A, B](other: FilterRoute2[A, B]) = new FilterRoute2[A, B](and(other.filter))
  def & [A, B, C](other: FilterRoute3[A, B, C]) = new FilterRoute3[A, B, C](and(other.filter))
  def & [A, B, C, D](other: FilterRoute4[A, B, C, D]) = new FilterRoute4[A, B, C, D](and(other.filter))
  def & [A, B, C, D, E](other: FilterRoute5[A, B, C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}

/**
 * A Route using the given RouteFilter function (which extracts 1 value) on all inner Routes it is applied to.
 */
class FilterRoute1[A](filter: RouteFilter[Tuple1[A]]) extends FilterRoute(filter) with ((A => Route) => Route) {
  def apply(routing: A => Route) = fromRouting { t => routing(t._1) }
  def | (other: FilterRoute1[A]) = new FilterRoute1[A](or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute1[A](and(other.filter))
  def & [B](other: FilterRoute1[B]) = new FilterRoute2[A, B](and(other.filter))
  def & [B, C](other: FilterRoute2[B, C]) = new FilterRoute3[A, B, C](and(other.filter))
  def & [B, C, D](other: FilterRoute3[B, C, D]) = new FilterRoute4[A, B, C, D](and(other.filter))
  def & [B, C, D, E](other: FilterRoute4[B, C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}

/**
 * A Route using the given RouteFilter function (which extracts 2 values) on all inner Routes it is applied to.
 */
class FilterRoute2[A, B](filter: RouteFilter[(A, B)]) extends FilterRoute(filter) with (((A, B) => Route) => Route) {
  def apply(routing: (A, B) => Route) = fromRouting { t => routing(t._1, t._2) }
  def | (other: FilterRoute2[A, B]) = new FilterRoute2[A, B](or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute2[A, B](and(other.filter))
  def & [C](other: FilterRoute1[C]) = new FilterRoute3[A, B, C](and(other.filter))
  def & [C, D](other: FilterRoute2[C, D]) = new FilterRoute4[A, B, C, D](and(other.filter))
  def & [C, D, E](other: FilterRoute3[C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}

/**
 * A Route using the given RouteFilter function (which extracts 3 values) on all inner Routes it is applied to.
 */
class FilterRoute3[A, B, C](filter: RouteFilter[(A, B, C)]) extends FilterRoute(filter) with (((A, B, C) => Route) => Route) {
  def apply(routing: (A, B, C) => Route) = fromRouting { t => routing(t._1, t._2, t._3) }
  def | (other: FilterRoute3[A, B, C]) = new FilterRoute3[A, B, C](or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute3[A, B, C](and(other.filter))
  def & [D](other: FilterRoute1[D]) = new FilterRoute4[A, B, C, D](and(other.filter))
  def & [D, E](other: FilterRoute2[D, E]) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}

/**
 * A Route using the given RouteFilter function (which extracts 4 values) on all inner Routes it is applied to.
 */
class FilterRoute4[A, B, C, D](filter: RouteFilter[(A, B, C, D)]) extends FilterRoute(filter) with (((A, B, C, D) => Route) => Route) {
  def apply(routing: (A, B, C, D) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4) }
  def | (other: FilterRoute4[A, B, C, D]) = new FilterRoute4[A, B, C, D](or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute4[A, B, C, D](and(other.filter))
  def & [E](other: FilterRoute1[E]) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}

/**
 * A Route using the given RouteFilter function (which extracts 5 values) on all inner Routes it is applied to.
 */
class FilterRoute5[A, B, C, D, E](filter: RouteFilter[(A, B, C, D, E)]) extends FilterRoute(filter) with (((A, B, C, D, E) => Route) => Route) {
  def apply(routing: (A, B, C, D, E) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5) }
  def | (other: FilterRoute5[A, B, C, D, E]) = new FilterRoute5[A, B, C, D, E](or(other.filter))
  def & (other: FilterRoute0) = new FilterRoute5[A, B, C, D, E](and(other.filter))
}
