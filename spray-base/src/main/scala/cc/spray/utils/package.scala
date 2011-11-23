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

  val EmptyByteArray = new Array[Byte](0)

  def identityFunc[T]: T => T = _identityFunc.asInstanceOf[T => T]
  private lazy val _identityFunc: Any => Any = x => x

  def dropFunc[T]: T => Unit = _dropFunc.asInstanceOf[T => Unit]
  private lazy val _dropFunc: Any => Unit = _ => ()

  // TODO: remove and replace with equivalent from the standard library once the resolution to issue 25578
  // (https://codereview.scala-lang.org/fisheye/changelog/scala-svn?cs=25578) has made it into a release
  def emptyPartialFunc[A, B] = EmptyPartial.asInstanceOf[PartialFunction[A, B]]
  private lazy val EmptyPartial = new PartialFunction[Any, Any] {
    def isDefinedAt(x: Any) = false
    def apply(x: Any) = throw new IllegalStateException
  }

  def make[A, U](a: A)(f: A => U): A = { f(a); a }

  // implicits
  implicit def pimpLinearSeq[A](seq: LinearSeq[A]): PimpedLinearSeq[A] = new PimpedLinearSeq[A](seq)
  implicit def pimpClass[A](clazz: Class[A]): PimpedClass[A] = new PimpedClass[A](clazz)
  implicit def pimpProduct(product: Product): PimpedProduct = new PimpedProduct(product)
  implicit def pimpRegex(regex: Regex) = new PimpedRegex(regex)
  implicit def pimpString(s: String) = new PimpedString(s)

}