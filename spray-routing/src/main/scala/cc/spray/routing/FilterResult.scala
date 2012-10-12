/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing

import shapeless.{HNil, HList}
import spray.util._


sealed trait FilterResult[+A <: HList] {
  def map[B <: HList](f: A => B): FilterResult[B]
  def flatMap[B <: HList](f: A => FilterResult[B]): FilterResult[B]
}

case class Pass[+A <: HList](values: A,
                             transform: RequestContext => RequestContext = identityFunc) extends FilterResult[A] {
  def map[B <: HList](f: A => B): FilterResult[B] = Pass(f(values))
  def flatMap[B <: HList](f: A => FilterResult[B]): FilterResult[B] = f(values)
}
object Pass {
  val Empty = Pass[HNil](HNil)
}

case class Reject(rejections: Seq[Rejection]) extends FilterResult[Nothing] {
  def map[B <: HList](f: Nothing => B): FilterResult[B] = this
  def flatMap[B <: HList](f: Nothing => FilterResult[B]): FilterResult[B] = this
}

object Reject {
  val Empty = Reject(Nil)
  def apply(rejection: Rejection): Reject = apply(rejection :: Nil)
}
