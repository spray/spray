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

package spray.http

import java.util.Arrays

sealed trait HttpEntity {
  def isEmpty: Boolean
  def buffer: Array[Byte]
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity
  def foreach(f: (ContentType, Array[Byte]) => Unit)
  def orElse(other: HttpEntity): HttpEntity
  def asString: String
  def toOption: Option[HttpBody]
}

case object EmptyEntity extends HttpEntity {
  def isEmpty: Boolean = true
  val buffer = new Array[Byte](0)
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity = this
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity = this
  def foreach(f: (ContentType, Array[Byte]) => Unit) {}
  def orElse(other: HttpEntity): HttpEntity = other
  def asString = ""
  def toOption = None
}

case class HttpBody(contentType: ContentType, buffer: Array[Byte]) extends HttpEntity {
  def isEmpty: Boolean = false
  def map(f: (ContentType, Array[Byte]) => (ContentType, Array[Byte])): HttpEntity = {
    val (ct, buf) = f(contentType, buffer)
    if (ct != contentType || (buf ne buffer)) new HttpBody(ct, buf) else this
  }
  def flatMap(f: (ContentType, Array[Byte]) => HttpEntity): HttpEntity = f(contentType, buffer)
  def foreach(f: (ContentType, Array[Byte]) => Unit) { f(contentType, buffer) }
  def orElse(other: HttpEntity): HttpEntity = this
  def asString = new String(buffer, contentType.charset.nioCharset)
  def toOption = Some(this)

  override def toString =
    "HttpBody(" + contentType + ',' + (if (buffer.length < 50) asString.take(50) + "..." else asString) + ')'

  override def hashCode = contentType.## * 31 + Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case x: HttpBody => (this eq x) || contentType == x.contentType && Arrays.equals(buffer, x.buffer)
    case _ => false
  }
}

object HttpBody {
  def apply(contentType: ContentType, string: String): HttpBody =
    new HttpBody(contentType, string.getBytes(contentType.charset.nioCharset))
  def apply(body: String): HttpBody =
    HttpBody(ContentType.`text/plain`, body)
}

object HttpEntity {
  implicit def apply(string: String): HttpEntity =
    if (string.isEmpty) EmptyEntity else HttpBody(string)

  implicit def apply(buffer: Array[Byte]): HttpEntity =
    if (buffer.length == 0) EmptyEntity else HttpBody(ContentType.`application/octet-stream`, buffer)

  implicit def apply(optionalBody: Option[HttpBody]): HttpEntity = optionalBody match {
    case Some(body) => body
    case None => EmptyEntity
  }
}


