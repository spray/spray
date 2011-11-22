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

import utils.Product0

/**
 * The FilterResult represents the two different filtering outcomes of RouteFilters:
 *  [[cc.spray.Pass]] or [[cc.spray.Reject]]
 */
sealed trait FilterResult[+T <: Product]

case class Reject(rejections: Set[Rejection] = Set.empty) extends FilterResult[Nothing]

object Reject {
  def apply(rejection: Rejection): Reject = apply(Set(rejection))
}

class Pass[+T <: Product](val values: T, val transform: RequestContext => RequestContext = identity) extends FilterResult[T]

object Pass {
  def apply(): Pass[Product0] = new Pass(Product0)
  def apply[A](a: A): Pass[Tuple1[A]] = new Pass(Tuple1(a))
  def apply[A, B](a: A, b: B): Pass[(A, B)] = new Pass((a, b))
  def apply[A, B, C](a: A, b: B, c: C): Pass[(A, B, C)] = new Pass((a, b, c))
  def apply[A, B, C, D](a: A, b: B, c: C, d: D): Pass[(A, B, C, D)] = new Pass((a, b, c, d))
  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E): Pass[(A, B, C, D, E)] = new Pass((a, b, c, d, e))
  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F): Pass[(A, B, C, D, E, F)] = new Pass((a, b, c, d, e, f))
  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G): Pass[(A, B, C, D, E, F, G)] = new Pass((a, b, c, d, e, f, g))

  def withTransform(transform: RequestContext => RequestContext) = new Pass(Product0, transform)
  def withTransform[A](a: A)(transform: RequestContext => RequestContext) = new Pass(Tuple1(a), transform)

  def unapply[T <: Product](pass: Pass[T]): Option[(T, RequestContext => RequestContext)] = Some(pass.values, pass.transform)
}