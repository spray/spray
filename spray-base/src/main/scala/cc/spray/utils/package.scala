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

import collection.immutable.LinearSeq
import util.matching.Regex

package object utils {

  private lazy val emptyPartial = new PartialFunction[Any, Any] {
    def isDefinedAt(x: Any) = false
    def apply(x: Any) = throw new IllegalStateException
  }

  def emptyPartialFunc[A, B] = emptyPartial.asInstanceOf[PartialFunction[A, B]]

  def make[A, U](a: A)(f: A => U): A = { f(a); a }

  // implicits
  implicit def pimpLinearSeq[A](seq: LinearSeq[A]): PimpedLinearSeq[A] = new PimpedLinearSeq[A](seq)
  implicit def pimpClass[A](clazz: Class[A]): PimpedClass[A] = new PimpedClass[A](clazz)
  implicit def pimpProduct(product: Product): PimpedProduct = new PimpedProduct(product)
  implicit def pimpRegex(regex: Regex) = new PimpedRegex(regex)
  implicit def pimpString(s: String) = new PimpedString(s)

}