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
package client

import http._
import HttpMethods._

sealed trait SimpleRequest[T] {
  def method: HttpMethod
  def uri: String
  def content: Option[T]
}

private[client] sealed abstract class SimpleRequestConstructors[T[_]] {
  def apply(): T[Nothing] = apply("/")
  def apply(uri: String): T[Nothing] = apply(uri, None)
  def apply[A](uri: String, content: A): T[A] = apply(uri, Some(content))
  def apply[A](uri: String, content: Option[A]): T[A]
}

object Get extends SimpleRequestConstructors[Get]
case class Get[T](uri: String, content: Option[T]) extends SimpleRequest[T] {
  def method = GET
}

object Post extends SimpleRequestConstructors[Post]
case class Post[T](uri: String = "/", content: Option[T] = None) extends SimpleRequest[T] {
  def method = POST
}

object Put extends SimpleRequestConstructors[Put]
case class Put[T](uri: String = "/", content: Option[T] = None) extends SimpleRequest[T] {
  def method = PUT
}

object Delete extends SimpleRequestConstructors[Delete]
case class Delete[T](uri: String = "/", content: Option[T] = None) extends SimpleRequest[T] {
  def method = DELETE
}