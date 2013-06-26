/*
 * Copyright (C) 2011-2013 spray.io
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

package spray

import scala.language.experimental.macros

import java.nio.ByteBuffer
import java.io.{ InputStream, File }
import java.nio.charset.Charset
import com.typesafe.config.Config
import scala.concurrent.duration.Duration
import scala.collection.LinearSeq
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.concurrent.Future
import akka.actor._
import util.pimps._

package object util {

  val EOL = System.getProperty("line.separator")
  val UTF8 = Charset.forName("UTF8")
  val EmptyByteArray = Array.empty[Byte]

  private[this] val _identityFunc: Any ⇒ Any = x ⇒ x
  def identityFunc[T]: T ⇒ T = _identityFunc.asInstanceOf[T ⇒ T]

  def tryToEither[T](body: ⇒ T): Either[Throwable, T] = tryOrElse(Right(body), Left(_))

  def tryOrElse[A, B >: A](body: ⇒ A, onError: Throwable ⇒ B): B =
    try body catch { case NonFatal(e) ⇒ onError(e) }

  /**
   * Requires that the given duration is greater than Duration.Zero (finite or infinite) or Duration.Undefined.
   * This implementation is macro-based and only works if the argument is an identifier or member selector.
   */
  def requirePositiveOrUndefined(duration: Duration): Duration = macro Macros.requirePositiveOrUndefined

  def actorSystem(implicit refFactory: ActorRefFactory): ExtendedActorSystem =
    refFactory match {
      case x: ActorContext        ⇒ actorSystem(x.system)
      case x: ExtendedActorSystem ⇒ x
      case x                      ⇒ throw new IllegalArgumentException("Unsupported ActorRefFactory implementation: " + refFactory)
    }

  // implicits
  implicit def pimpActorSystem(system: ActorSystem): PimpedActorSystem = new PimpedActorSystem(system)
  implicit def pimpAny_[T](any: T): PimpedAny[T] = new PimpedAny(any)
  implicit def pimpByteArray(array: Array[Byte]): PimpedByteArray = new PimpedByteArray(array)
  implicit def pimpByteBuffer(buf: ByteBuffer): PimpedByteBuffer = new PimpedByteBuffer(buf)
  implicit def pimpClass[T](clazz: Class[T]): PimpedClass[T] = new PimpedClass[T](clazz)
  implicit def pimpConfig(config: Config): PimpedConfig = new PimpedConfig(config)
  implicit def pimpDuration(duration: Duration): PimpedDuration = new PimpedDuration(duration)
  implicit def pimpFile(file: File): PimpedFile = new PimpedFile(file)
  implicit def pimpFuture[T](fut: Future[T]): PimpedFuture[T] = new PimpedFuture[T](fut)
  implicit def pimpInputStream(inputStream: InputStream): PimpedInputStream = new PimpedInputStream(inputStream)
  implicit def pimpSeq[T](seq: Seq[T]): PimpedSeq[T] = seq match {
    case x: LinearSeq[_]  ⇒ new PimpedLinearSeq[T](x)
    case x: IndexedSeq[_] ⇒ new PimpedIndexedSeq[T](x)
  }
  implicit def pimpRegex(regex: Regex): PimpedRegex = new PimpedRegex(regex)
  implicit def pimpString_(s: String): PimpedString = new PimpedString(s)
  implicit def pimpEither[A, B](either: Either[A, B]): Either.RightProjection[A, B] = either.right
}
