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

import http.HttpResponse
import utils.Product0

/**
 * The RoutingResult represents the two different options for the way Routes can act upon a request:
 * [[Respond]] or [[Reject]]
 */
sealed trait RoutingResult

case class Respond(response: HttpResponse) extends RoutingResult

/**
 * The FilterResult represents the two different filtering outcomes of RouteFilters:
 * [[Pass]] or [[Reject]]
 */
sealed trait FilterResult[+T <: Product]

case class Reject(rejections: Set[Rejection] = Set.empty) extends RoutingResult with FilterResult[Nothing]

object Reject {
  def apply(rejection: Rejection): Reject = apply(Set(rejection))
}

class Pass[+T <: Product](val values: T, val transform: RequestContext => RequestContext) extends FilterResult[T]

object Pass {
  def apply(): Pass[Product0] = new Pass(Product0, transform = identity)
  def apply[A](a: A): Pass[Tuple1[A]] = new Pass(Tuple1(a), transform = identity)
  def apply[A, B](a: A, b: B): Pass[(A, B)] = new Pass((a, b), transform = identity)
  def apply[A, B, C](a: A, b: B, c: C): Pass[(A, B, C)] = new Pass((a, b, c), transform = identity)
  def apply[A, B, C, D](a: A, b: B, c: C, d: D): Pass[(A, B, C, D)] = new Pass((a, b, c, d), transform = identity)
  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E): Pass[(A, B, C, D, E)] = new Pass((a, b, c, d, e), transform = identity)
  
  def unapply[T <: Product](pass: Pass[T]): Option[(T, RequestContext => RequestContext)] = Some(pass.values, pass.transform)
}