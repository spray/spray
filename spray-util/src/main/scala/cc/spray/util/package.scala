/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import collection.LinearSeq
import akka.dispatch.Future
import akka.actor.ActorSystem

package object util {

  val EmptyByteArray = new Array[Byte](0)

  def identityFunc[T]: T => T = _identityFunc.asInstanceOf[T => T]
  private lazy val _identityFunc: Any => Any = x => x

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
  implicit def pimpByteArray(array: Array[Byte]): PimpedByteArray = new PimpedByteArray(array)
  implicit def pimpFuture[A](fut: Future[A]): PimpedFuture[A] = new PimpedFuture[A](fut)
  implicit def pimpActorSystem(system: ActorSystem): PimpedActorSystem = new PimpedActorSystem(system)

}