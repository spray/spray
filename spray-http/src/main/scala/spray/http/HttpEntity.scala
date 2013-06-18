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

package spray.http

import java.util

/**
 * Models the entity (aka "body" or "content) of an HTTP message.
 */
sealed trait HttpEntity {
  def isEmpty: Boolean
  def buffer: Array[Byte]
  def flatMap(f: HttpBody ⇒ HttpEntity): HttpEntity
  def orElse(other: HttpEntity): HttpEntity
  def asString: String
  def asString(defaultCharset: HttpCharset): String
  def toOption: Option[HttpBody]
}

/**
 * Models an empty entity.
 */
case object EmptyEntity extends HttpEntity {
  def isEmpty: Boolean = true
  val buffer = Array.empty[Byte]
  def flatMap(f: HttpBody ⇒ HttpEntity): HttpEntity = this
  def orElse(other: HttpEntity): HttpEntity = other
  def asString = ""
  def asString(defaultCharset: HttpCharset) = ""
  def toOption = None
}

/**
 * Models a non-empty entity. The buffer array is guaranteed to have a size greater than zero.
 * CAUTION: Even though the byte array is directly exposed for performance reasons all instances of this class are
 * assumed to be immutable! spray never modifies the buffer contents after an HttpBody instance has been created.
 * If you modify the buffer contents by writing to the array things WILL BREAK!
 */
case class HttpBody private (contentType: ContentType, buffer: Array[Byte]) extends HttpEntity {
  def isEmpty: Boolean = false
  def flatMap(f: HttpBody ⇒ HttpEntity): HttpEntity = f(this)
  def orElse(other: HttpEntity): HttpEntity = this
  def asString = new String(buffer, contentType.charset.nioCharset)
  def asString(defaultCharset: HttpCharset) =
    new String(buffer, contentType.definedCharset.getOrElse(defaultCharset).nioCharset)
  def toOption = Some(this)

  override def toString =
    "HttpEntity(" + contentType + ',' + (if (buffer.length > 500) asString.take(500) + "..." else asString) + ')'

  override def hashCode = contentType.## * 31 + util.Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case x: HttpBody ⇒ (this eq x) || contentType == x.contentType && util.Arrays.equals(buffer, x.buffer)
    case _           ⇒ false
  }
}

object HttpBody {
  private[http] def from(contentType: ContentType, buffer: Array[Byte]): HttpEntity =
    if (buffer.length == 0) EmptyEntity else new HttpBody(contentType, buffer)
}

object HttpEntity {
  implicit def apply(string: String): HttpEntity =
    apply(ContentTypes.`text/plain`, string)

  implicit def apply(buffer: Array[Byte]): HttpEntity =
    apply(ContentTypes.`application/octet-stream`, buffer)

  implicit def flatten(optionalEntity: Option[HttpEntity]): HttpEntity =
    optionalEntity match {
      case Some(body) ⇒ body
      case None       ⇒ EmptyEntity
    }

  def apply(contentType: ContentType, string: String): HttpEntity =
    if (string.isEmpty) EmptyEntity
    else apply(contentType, string.getBytes(contentType.charset.nioCharset))

  def apply(contentType: ContentType, buffer: Array[Byte]): HttpEntity = HttpBody.from(contentType, buffer)
}
