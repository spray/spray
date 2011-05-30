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

import utils.Product0

private[spray] trait BasicDirectives {
  
  /**
   * Creates a [[cc.spray.FilterRoute0]] that accepts all requests but applies to given transformation function to
   * the RequestContext.
   */
  def transform(f: RequestContext => RequestContext) = new TransformerRoute(f)

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
  
  /**
   * Creates a [[cc.spray.FilterRoute6]] from the given RouteFilter function. 
   */
  def filter6[A, B, C, D, E, F](filter: RouteFilter[(A, B, C, D, E, F)]) = new FilterRoute6(filter)
  
  /**
   * Creates a [[cc.spray.FilterRoute7]] from the given RouteFilter function. 
   */
  def filter7[A, B, C, D, E, F, G](filter: RouteFilter[(A, B, C, D, E, F, G)]) = new FilterRoute7(filter)
}

abstract class FilterRoute[T <: Product](val filter: RouteFilter[T]) { self =>
  protected def fromRouting(f: T => Route): Route = { ctx =>
    filter(ctx) match {
      case Pass(values, transform) => f(values)(transform(ctx)) 
      case Reject(rejections) => ctx.reject(rejections)
    }
  }
  protected def or(other: FilterRoute[T]): RouteFilter[T] = { ctx =>
    self.filter(ctx) match {
      case x: Pass[_] => x
      case Reject(rejections1) => other.filter(ctx) match {
        case x: Pass[_] => x
        case Reject(rejections2) => Reject(rejections1 ++ rejections2) 
      }
    }
  } 
  protected def and[S <: Product, R <: Product](other: FilterRoute[S]): RouteFilter[R] = { ctx =>
    self.filter(ctx) match {
      case Pass(values1, transform1) => other.filter(transform1(ctx)) match {
        case Pass(values2, transform2) => {
          new Pass((values1 productJoin values2).asInstanceOf[R], transform1 andThen transform2)          
        }
        case x: Reject => x
      }
      case x: Reject => x
    }
  }

  /**
   * Negates this filter, i.e. the inner route will be evaluated if this filter rejects and be rejected of this passes.
   * Note that negated filters completely loose any specific characteristics of their underlying filter:
   * They never extract anything, they do not apply the potential context transformation of their underlying filter
   * and they do not create any specific rejection instances if they don't pass.
   */
  def unary_! : FilterRoute0 = new FilterRoute0( ctx =>
    filter(ctx) match {
      case _: Pass[_] => Reject() 
      case _: Reject => Pass()
    }
  )
}

/**
 * A Route using the given RouteFilter function on all inner Routes it is applied to.
 */
class FilterRoute0(filter: RouteFilter[Product0]) extends FilterRoute(filter) with (Route => Route) {  
  def apply(route: Route) = fromRouting { _ => route }
  def | (other: FilterRoute0) = new FilterRoute0(or(other))
  def & (other: FilterRoute0) = new FilterRoute0(and(other))
  def & [A](other: FilterRoute1[A]) = new FilterRoute1[A](and(other))
  def & [A, B](other: FilterRoute2[A, B]) = new FilterRoute2[A, B](and(other))
  def & [A, B, C](other: FilterRoute3[A, B, C]) = new FilterRoute3[A, B, C](and(other))
  def & [A, B, C, D](other: FilterRoute4[A, B, C, D]) = new FilterRoute4[A, B, C, D](and(other))
  def & [A, B, C, D, E](other: FilterRoute5[A, B, C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [A, B, C, D, E, F](other: FilterRoute6[A, B, C, D, E, F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [A, B, C, D, E, F, G](other: FilterRoute7[A, B, C, D, E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 1 value) on all inner Routes it is applied to.
 */
class FilterRoute1[A](filter: RouteFilter[Tuple1[A]]) extends FilterRoute(filter) with ((A => Route) => Route) {
  def apply(routing: A => Route) = fromRouting { t => routing(t._1) }
  def | (other: FilterRoute1[A]) = new FilterRoute1[A](or(other))
  def & (other: FilterRoute0) = new FilterRoute1[A](and(other))
  def & [B](other: FilterRoute1[B]) = new FilterRoute2[A, B](and(other))
  def & [B, C](other: FilterRoute2[B, C]) = new FilterRoute3[A, B, C](and(other))
  def & [B, C, D](other: FilterRoute3[B, C, D]) = new FilterRoute4[A, B, C, D](and(other))
  def & [B, C, D, E](other: FilterRoute4[B, C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [B, C, D, E, F](other: FilterRoute5[B, C, D, E, F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [B, C, D, E, F, G](other: FilterRoute6[B, C, D, E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 2 values) on all inner Routes it is applied to.
 */
class FilterRoute2[A, B](filter: RouteFilter[(A, B)]) extends FilterRoute(filter) with (((A, B) => Route) => Route) {
  def apply(routing: (A, B) => Route) = fromRouting { t => routing(t._1, t._2) }
  def | (other: FilterRoute2[A, B]) = new FilterRoute2[A, B](or(other))
  def & (other: FilterRoute0) = new FilterRoute2[A, B](and(other))
  def & [C](other: FilterRoute1[C]) = new FilterRoute3[A, B, C](and(other))
  def & [C, D](other: FilterRoute2[C, D]) = new FilterRoute4[A, B, C, D](and(other))
  def & [C, D, E](other: FilterRoute3[C, D, E]) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [C, D, E, F](other: FilterRoute4[C, D, E, F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [C, D, E, F, G](other: FilterRoute5[C, D, E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 3 values) on all inner Routes it is applied to.
 */
class FilterRoute3[A, B, C](filter: RouteFilter[(A, B, C)]) extends FilterRoute(filter) with (((A, B, C) => Route) => Route) {
  def apply(routing: (A, B, C) => Route) = fromRouting { t => routing(t._1, t._2, t._3) }
  def | (other: FilterRoute3[A, B, C]) = new FilterRoute3[A, B, C](or(other))
  def & (other: FilterRoute0) = new FilterRoute3[A, B, C](and(other))
  def & [D](other: FilterRoute1[D]) = new FilterRoute4[A, B, C, D](and(other))
  def & [D, E](other: FilterRoute2[D, E]) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [D, E, F](other: FilterRoute3[D, E, F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [D, E, F, G](other: FilterRoute4[D, E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 4 values) on all inner Routes it is applied to.
 */
class FilterRoute4[A, B, C, D](filter: RouteFilter[(A, B, C, D)]) extends FilterRoute(filter) with (((A, B, C, D) => Route) => Route) {
  def apply(routing: (A, B, C, D) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4) }
  def | (other: FilterRoute4[A, B, C, D]) = new FilterRoute4[A, B, C, D](or(other))
  def & (other: FilterRoute0) = new FilterRoute4[A, B, C, D](and(other))
  def & [E](other: FilterRoute1[E]) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [E, F](other: FilterRoute2[E, F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [E, F, G](other: FilterRoute3[E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 5 values) on all inner Routes it is applied to.
 */
class FilterRoute5[A, B, C, D, E](filter: RouteFilter[(A, B, C, D, E)]) extends FilterRoute(filter) with (((A, B, C, D, E) => Route) => Route) {
  def apply(routing: (A, B, C, D, E) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5) }
  def | (other: FilterRoute5[A, B, C, D, E]) = new FilterRoute5[A, B, C, D, E](or(other))
  def & (other: FilterRoute0) = new FilterRoute5[A, B, C, D, E](and(other))
  def & [F](other: FilterRoute1[F]) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [F, G](other: FilterRoute2[F, G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 6 values) on all inner Routes it is applied to.
 */
class FilterRoute6[A, B, C, D, E, F](filter: RouteFilter[(A, B, C, D, E, F)]) extends FilterRoute(filter) with (((A, B, C, D, E, F) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6) }
  def | (other: FilterRoute6[A, B, C, D, E, F]) = new FilterRoute6[A, B, C, D, E, F](or(other))
  def & (other: FilterRoute0) = new FilterRoute6[A, B, C, D, E, F](and(other))
  def & [G](other: FilterRoute1[G]) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 7 values) on all inner Routes it is applied to.
 */
class FilterRoute7[A, B, C, D, E, F, G](filter: RouteFilter[(A, B, C, D, E, F, G)]) extends FilterRoute(filter) with (((A, B, C, D, E, F, G) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F, G) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6, t._7) }
  def | (other: FilterRoute7[A, B, C, D, E, F, G]) = new FilterRoute7[A, B, C, D, E, F, G](or(other))
  def & (other: FilterRoute0) = new FilterRoute7[A, B, C, D, E, F, G](and(other))
}

class TransformerRoute(transform: RequestContext => RequestContext)
        extends FilterRoute0(_ => new Pass(Product0, transform = transform))