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

package cc.spray
package directives

import util._
import typeconversion._
import pimps.Product0

private[spray] trait BasicDirectives {

  /**
   * Creates a [[cc.spray.SprayRoute0]] from the given RouteFilter function.
   */
  def filter(filter: RouteFilter[Product0]) = new SprayRoute0(filter)
  
  /**
   * Created a [[cc.spray.SprayRoute1]] from the given RouteFilter function.
   */
  def filter1[A](filter: RouteFilter[Tuple1[A]]) = new SprayRoute1(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute2]] from the given RouteFilter function.
   */
  def filter2[A, B](filter: RouteFilter[(A, B)]) = new SprayRoute2(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute3]] from the given RouteFilter function.
   */
  def filter3[A, B, C](filter: RouteFilter[(A, B, C)]) = new SprayRoute3(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute4]] from the given RouteFilter function.
   */
  def filter4[A, B, C, D](filter: RouteFilter[(A, B, C, D)]) = new SprayRoute4(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute5]] from the given RouteFilter function.
   */
  def filter5[A, B, C, D, E](filter: RouteFilter[(A, B, C, D, E)]) = new SprayRoute5(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute6]] from the given RouteFilter function.
   */
  def filter6[A, B, C, D, E, F](filter: RouteFilter[(A, B, C, D, E, F)]) = new SprayRoute6(filter)
  
  /**
   * Creates a [[cc.spray.SprayRoute7]] from the given RouteFilter function.
   */
  def filter7[A, B, C, D, E, F, G](filter: RouteFilter[(A, B, C, D, E, F, G)]) = new SprayRoute7(filter)

  /**
   * Creates a [[cc.spray.SprayRoute8]] from the given RouteFilter function.
   */
  def filter8[A, B, C, D, E, F, G, H](filter: RouteFilter[(A, B, C, D, E, F, G, H)]) = new SprayRoute8(filter)

  /**
   * Creates a [[cc.spray.SprayRoute9]] from the given RouteFilter function.
   */
  def filter9[A, B, C, D, E, F, G, H, I](filter: RouteFilter[(A, B, C, D, E, F, G, H, I)]) = new SprayRoute9(filter)

  /**
   * Creates a [[cc.spray.SprayRoute0]] that accepts all requests but applies the given transformation function to
   * the RequestContext.
   */
  def transformRequestContext(f: RequestContext => RequestContext) =
    new SprayRoute0(_ => new Pass(Product0, transform = f))

  /**
   * Creates a [[cc.spray.TransformRoute]] that applies the given transformation function to its inner Route.
   */
  def transformRoute(f: Route => Route) = new SprayRoute0(Pass.Always) {
    override def apply(route: Route) = f(route)
  }

  /**
   * A directive that does nothing but delegate route handling to its inner route.
   */
  def alwaysPass = filter(Pass.Always)

  /**
   * Returns a route that always extract the given value.
   */
  def provide[A](value: A) = filter1(_ => Pass(value))
}

sealed abstract class SprayRoute[T <: Product](val filter: RouteFilter[T]) { self =>
  protected def fromRouting(f: T => Route): Route = { ctx =>
    filter(ctx) match {
      case Pass(values, transform) => f(values)(transform(ctx)) 
      case Reject(rejections) => ctx.reject(rejections)
    }
  }
  protected def or(other: SprayRoute[T]): RouteFilter[T] = { ctx =>
    self.filter(ctx) match {
      case x: Pass[_] => x
      case Reject(rejections1) => other.filter(ctx) match {
        case x: Pass[_] => x
        case Reject(rejections2) => Reject(rejections1 ++ rejections2) 
      }
    }
  } 
  protected def and[S <: Product, R <: Product](other: SprayRoute[S]): RouteFilter[R] = { ctx =>
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
   * Negates this filter, i.e. the inner route will be evaluated if this filter rejects and be rejected if this passes.
   * Note that negated filters completely loose any specific characteristics of their underlying filter:
   * They never extract anything, they do not apply the potential context transformation of their underlying filter
   * and they do not create any specific rejection instances if they don't pass.
   */
  def unary_! : SprayRoute0 = new SprayRoute0( ctx =>
    filter(ctx) match {
      case _: Pass[_] => Reject() 
      case _: Reject => Pass
    }
  )

  protected def convert[P <: Product](deserializer: Deserializer[T, P]): SprayRoute1[P] = {
    new SprayRoute1( ctx =>
      self.filter(ctx) match {
        case Pass(values, transform) => deserializer(values) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(MalformedContent(msg)) => Reject(ValidationRejection(msg))
          case Left(error) => Reject(ValidationRejection(error.toString))
        }
        case x: Reject => x
      }
    )
  }
}

/**
 * A Route using the given RouteFilter function on all inner Routes it is applied to.
 */
class SprayRoute0(filter: RouteFilter[Product0]) extends SprayRoute(filter) with (Route => Route) {
  def apply(route: Route) = fromRouting { _ => route }
  def | (other: SprayRoute0) = new SprayRoute0(or(other))
  def & (other: SprayRoute0) = new SprayRoute0(and(other))
  def & [A](other: SprayRoute1[A]) = new SprayRoute1[A](and(other))
  def & [A, B](other: SprayRoute2[A, B]) = new SprayRoute2[A, B](and(other))
  def & [A, B, C](other: SprayRoute3[A, B, C]) = new SprayRoute3[A, B, C](and(other))
  def & [A, B, C, D](other: SprayRoute4[A, B, C, D]) = new SprayRoute4[A, B, C, D](and(other))
  def & [A, B, C, D, E](other: SprayRoute5[A, B, C, D, E]) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [A, B, C, D, E, F](other: SprayRoute6[A, B, C, D, E, F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [A, B, C, D, E, F, G](other: SprayRoute7[A, B, C, D, E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [A, B, C, D, E, F, G, H](other: SprayRoute8[A, B, C, D, E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [A, B, C, D, E, F, G, H, I](other: SprayRoute9[A, B, C, D, E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
}

/**
 * A Route using the given RouteFilter function (which extracts 1 value) on all inner Routes it is applied to.
 */
class SprayRoute1[A](filter: RouteFilter[Tuple1[A]]) extends SprayRoute(filter) with ((A => Route) => Route) {
  def apply(routing: A => Route) = fromRouting { t => routing(t._1) }
  def | (other: SprayRoute1[A]) = new SprayRoute1[A](or(other))
  def & (other: SprayRoute0) = new SprayRoute1[A](and(other))
  def & [B](other: SprayRoute1[B]) = new SprayRoute2[A, B](and(other))
  def & [B, C](other: SprayRoute2[B, C]) = new SprayRoute3[A, B, C](and(other))
  def & [B, C, D](other: SprayRoute3[B, C, D]) = new SprayRoute4[A, B, C, D](and(other))
  def & [B, C, D, E](other: SprayRoute4[B, C, D, E]) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [B, C, D, E, F](other: SprayRoute5[B, C, D, E, F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [B, C, D, E, F, G](other: SprayRoute6[B, C, D, E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [B, C, D, E, F, G, H](other: SprayRoute7[B, C, D, E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [B, C, D, E, F, G, H, I](other: SprayRoute8[B, C, D, E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[Tuple1[A], T]) = convert(deserializer)
  def map[B](f: A => B): SprayRoute1[B] = new SprayRoute1[B](filter(_).map(t => Tuple1(f(t._1))))
  def flatMap[B](f: A => FilterResult[Tuple1[B]]): SprayRoute1[B] = new SprayRoute1[B](filter(_).flatMap(t => f(t._1)))
}

/**
 * A Route using the given RouteFilter function (which extracts 2 values) on all inner Routes it is applied to.
 */
class SprayRoute2[A, B](filter: RouteFilter[(A, B)]) extends SprayRoute(filter) with (((A, B) => Route) => Route) {
  def apply(routing: (A, B) => Route) = fromRouting { t => routing(t._1, t._2) }
  def | (other: SprayRoute2[A, B]) = new SprayRoute2[A, B](or(other))
  def & (other: SprayRoute0) = new SprayRoute2[A, B](and(other))
  def & [C](other: SprayRoute1[C]) = new SprayRoute3[A, B, C](and(other))
  def & [C, D](other: SprayRoute2[C, D]) = new SprayRoute4[A, B, C, D](and(other))
  def & [C, D, E](other: SprayRoute3[C, D, E]) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [C, D, E, F](other: SprayRoute4[C, D, E, F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [C, D, E, F, G](other: SprayRoute5[C, D, E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [C, D, E, F, G, H](other: SprayRoute6[C, D, E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [C, D, E, F, G, H, I](other: SprayRoute7[C, D, E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 3 values) on all inner Routes it is applied to.
 */
class SprayRoute3[A, B, C](filter: RouteFilter[(A, B, C)]) extends SprayRoute(filter) with (((A, B, C) => Route) => Route) {
  def apply(routing: (A, B, C) => Route) = fromRouting { t => routing(t._1, t._2, t._3) }
  def | (other: SprayRoute3[A, B, C]) = new SprayRoute3[A, B, C](or(other))
  def & (other: SprayRoute0) = new SprayRoute3[A, B, C](and(other))
  def & [D](other: SprayRoute1[D]) = new SprayRoute4[A, B, C, D](and(other))
  def & [D, E](other: SprayRoute2[D, E]) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [D, E, F](other: SprayRoute3[D, E, F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [D, E, F, G](other: SprayRoute4[D, E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [D, E, F, G, H](other: SprayRoute5[D, E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [D, E, F, G, H, I](other: SprayRoute6[D, E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 4 values) on all inner Routes it is applied to.
 */
class SprayRoute4[A, B, C, D](filter: RouteFilter[(A, B, C, D)]) extends SprayRoute(filter) with (((A, B, C, D) => Route) => Route) {
  def apply(routing: (A, B, C, D) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4) }
  def | (other: SprayRoute4[A, B, C, D]) = new SprayRoute4[A, B, C, D](or(other))
  def & (other: SprayRoute0) = new SprayRoute4[A, B, C, D](and(other))
  def & [E](other: SprayRoute1[E]) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [E, F](other: SprayRoute2[E, F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [E, F, G](other: SprayRoute3[E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [E, F, G, H](other: SprayRoute4[E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [E, F, G, H, I](other: SprayRoute5[E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 5 values) on all inner Routes it is applied to.
 */
class SprayRoute5[A, B, C, D, E](filter: RouteFilter[(A, B, C, D, E)]) extends SprayRoute(filter) with (((A, B, C, D, E) => Route) => Route) {
  def apply(routing: (A, B, C, D, E) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5) }
  def | (other: SprayRoute5[A, B, C, D, E]) = new SprayRoute5[A, B, C, D, E](or(other))
  def & (other: SprayRoute0) = new SprayRoute5[A, B, C, D, E](and(other))
  def & [F](other: SprayRoute1[F]) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [F, G](other: SprayRoute2[F, G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [F, G, H](other: SprayRoute3[F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [F, G, H, I](other: SprayRoute4[F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D, E), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 6 values) on all inner Routes it is applied to.
 */
class SprayRoute6[A, B, C, D, E, F](filter: RouteFilter[(A, B, C, D, E, F)]) extends SprayRoute(filter) with (((A, B, C, D, E, F) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6) }
  def | (other: SprayRoute6[A, B, C, D, E, F]) = new SprayRoute6[A, B, C, D, E, F](or(other))
  def & (other: SprayRoute0) = new SprayRoute6[A, B, C, D, E, F](and(other))
  def & [G](other: SprayRoute1[G]) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [G, H](other: SprayRoute2[G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [G, H, I](other: SprayRoute3[G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D, E, F), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 7 values) on all inner Routes it is applied to.
 */
class SprayRoute7[A, B, C, D, E, F, G](filter: RouteFilter[(A, B, C, D, E, F, G)]) extends SprayRoute(filter) with (((A, B, C, D, E, F, G) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F, G) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6, t._7) }
  def | (other: SprayRoute7[A, B, C, D, E, F, G]) = new SprayRoute7[A, B, C, D, E, F, G](or(other))
  def & (other: SprayRoute0) = new SprayRoute7[A, B, C, D, E, F, G](and(other))
  def & [H](other: SprayRoute1[H]) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [H, I](other: SprayRoute2[H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D, E, F, G), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 8 values) on all inner Routes it is applied to.
 */
class SprayRoute8[A, B, C, D, E, F, G, H](filter: RouteFilter[(A, B, C, D, E, F, G, H)]) extends SprayRoute(filter) with (((A, B, C, D, E, F, G, H) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F, G, H) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8) }
  def | (other: SprayRoute8[A, B, C, D, E, F, G, H]) = new SprayRoute8[A, B, C, D, E, F, G, H](or(other))
  def & (other: SprayRoute0) = new SprayRoute8[A, B, C, D, E, F, G, H](and(other))
  def & [I](other: SprayRoute1[I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D, E, F, G, H), T]) = convert(deserializer)
}

/**
 * A Route using the given RouteFilter function (which extracts 9 values) on all inner Routes it is applied to.
 */
class SprayRoute9[A, B, C, D, E, F, G, H, I](filter: RouteFilter[(A, B, C, D, E, F, G, H, I)]) extends SprayRoute(filter) with (((A, B, C, D, E, F, G, H, I) => Route) => Route) {
  def apply(routing: (A, B, C, D, E, F, G, H, I) => Route) = fromRouting { t => routing(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9) }
  def | (other: SprayRoute9[A, B, C, D, E, F, G, H, I]) = new SprayRoute9[A, B, C, D, E, F, G, H, I](or(other))
  def & (other: SprayRoute0) = new SprayRoute9[A, B, C, D, E, F, G, H, I](and(other))
  def as[T <: Product](deserializer: Deserializer[(A, B, C, D, E, F, G, H, I), T]) = convert(deserializer)
}