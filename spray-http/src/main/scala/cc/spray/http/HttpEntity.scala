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

package cc.spray.http

import java.util.Arrays

sealed trait HttpEntity {
  def isEmpty: Boolean
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity
  def foreach(f: (ContentType, Array[Byte]) => Unit)
  def orElse(other: HttpEntity): HttpEntity
}

case object EmptyEntity extends HttpEntity {
  def isEmpty: Boolean = true
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity = this
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity = this
  def foreach(f: (ContentType, Array[Byte]) => Unit) {}
  def orElse(other: HttpEntity): HttpEntity = other
}

case class HttpBody(contentType: ContentType, buffer: Array[Byte]) extends HttpEntity {
  def isEmpty: Boolean = false
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity = {
    val (ct, buf) = f(contentType, buffer)
    new HttpBody(ct, buf)
  }
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity = f(contentType, buffer)
  def foreach(f: (ContentType, Array[Byte]) => Unit) { f(contentType, buffer) }
  def orElse(other: HttpEntity): HttpEntity = this

  override def toString =
    if (buffer.length < 50)
      "HttpBody(" + contentType + ',' + new String(buffer, contentType.charset.nioCharset) + ')'
    else
      "HttpBody(" + contentType + ',' + new String(buffer, contentType.charset.nioCharset).take(50) + "...)"

  override def hashCode = contentType.## * 31 + Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case x: HttpBody => (this eq x) || contentType == x.contentType && Arrays.equals(buffer, x.buffer)
    case _ => false
  }
}

object HttpEntity {
  def apply(string: String): HttpEntity = apply(ContentType.DefaultTextPlain, string)

  def apply(contentType: ContentType, string: String): HttpEntity =
    new HttpBody(contentType, string.getBytes(contentType.charset.nioCharset))

  implicit def string2HttpEntity(s: String): HttpEntity =
    if (s.isEmpty) EmptyEntity else apply(s)
}


