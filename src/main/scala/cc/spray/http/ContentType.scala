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

package cc.spray.http

import Charsets._

case class ContentTypeRange(mediaRange: MediaRange, charsetRange: CharsetRange = `*`) {
  def value: String = if (charsetRange == `*`) mediaRange.value else {
    mediaRange.value + "; charset=" + charsetRange.value
  }
  def matches(contentType: ContentType) = {
    mediaRange.matches(contentType.mediaType) &&
            (charsetRange == `*` || contentType.charset.map(charsetRange.matches(_)).getOrElse(false))
  }
  
  override def toString = "ContentTypeRange(" + value + ')'
}

class ContentType private (val mediaType: MediaType, val charset: Option[Charset]) {
  def value: String = charset match {
    case Some(cs) => mediaType.value + "; charset=" + cs.value
    case None => mediaType.value
  }

  override def equals(obj: Any) = obj match {
    case x: ContentType => mediaType == x.mediaType && charset == x.charset
    case _ => false
  }

  override def hashCode() = 31 * mediaType.## + charset.##

  override def toString = "ContentType(" + value + ")"
}

object ContentType {
  def apply(mediaType: MediaType, charset: Charset): ContentType = apply(mediaType, Some(charset))
  
  def apply(mediaType: MediaType, charset: Option[Charset] = None): ContentType = {
    new ContentType(mediaType, if (mediaType.isText && charset.isEmpty) Some(`ISO-8859-1`) else charset)
  }
  
  def unapply(ct: ContentType): Option[(MediaType, Option[Charset])] = Some((ct.mediaType, ct.charset))
  
  implicit def fromMimeType(mimeType: MediaType): ContentType = ContentType(mimeType) 
}                     