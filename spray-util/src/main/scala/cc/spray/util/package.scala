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

import java.nio.ByteBuffer
import scala.collection.LinearSeq
import scala.util.matching.Regex
import akka.util.NonFatal
import akka.dispatch.Future
import akka.actor.{ActorRefFactory, ActorSystem}
import annotation.tailrec
import util.pimps._


package object util {

  val EOL = System.getProperty("line.separator")
  val EmptyByteArray = new Array[Byte](0)

  def identityFunc[T]: T => T = _identityFunc.asInstanceOf[T => T]
  private val _identityFunc: Any => Any = x => x

  def make[A, U](a: A)(f: A => U): A = { f(a); a }

  @tailrec
  def tfor[@specialized T](i: T)(test: T => Boolean, inc: T => T)(f: T => Unit) {
    if(test(i)) {
      f(i)
      tfor(inc(i))(test, inc)(f)
    }
  }

  def tryToEither[T](body: => T): Either[Throwable, T] = tryOrElse(Right(body), Left(_))

  def tryOrElse[A, B >: A](body: => A, onError: Throwable => B): B =
    try body catch { case NonFatal(e) => onError(e) }

  // implicits
  implicit def pimpActorSystem(system: ActorSystem)     :PimpedActorSystem     = new PimpedActorSystem(system)
  implicit def pimpActorRefFactory(f: ActorRefFactory)  :PimpedActorRefFactory = new PimpedActorRefFactory(f)
  implicit def pimpAny[T](any: T)                       :PimpedAny[T]          = new PimpedAny(any)
  implicit def pimpByteArray(array: Array[Byte])        :PimpedByteArray       = new PimpedByteArray(array)
  implicit def pimpByteBuffer(buf: ByteBuffer)          :PimpedByteBuffer      = new PimpedByteBuffer(buf)
  implicit def pimpClass[A](clazz: Class[A])            :PimpedClass[A]        = new PimpedClass[A](clazz)
  implicit def pimpFuture[A](fut: Future[A])            :PimpedFuture[A]       = new PimpedFuture[A](fut)
  implicit def pimpSeq[A](seq: Seq[A])                  :PimpedSeq[A]          = seq match {
    case x: LinearSeq[_] => new PimpedLinearSeq[A](x)
    case x: IndexedSeq[_] => new PimpedIndexedSeq[A](x)
  }
  implicit def pimpRegex(regex: Regex)                  :PimpedRegex           = new PimpedRegex(regex)
  implicit def pimpString(s: String)                    :PimpedString          = new PimpedString(s)
  implicit def pimpEither[A, B](either: Either[A, B])   :Either.RightProjection[A, B] = either.right
}