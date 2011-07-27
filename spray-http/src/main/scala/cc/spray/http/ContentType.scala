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

import HttpCharsets._

case class ContentTypeRange(mediaRange: MediaRange, charsetRange: HttpCharsetRange = `*`) {
  def value: String = if (charsetRange == `*`) mediaRange.value else {
    mediaRange.value + "; charset=" + charsetRange.value
  }
  def matches(contentType: ContentType) = {
    mediaRange.matches(contentType.mediaType) &&
            (charsetRange == `*` || contentType.charset.map(charsetRange.matches(_)).getOrElse(false))
  }
  
  override def toString = "ContentTypeRange(" + value + ')'
}

case class ContentType(mediaType: MediaType, charset: Option[HttpCharset]) {
  def value: String = charset match {
    // don't print the charset parameter if it's the default charset
    case Some(cs) if (!mediaType.isText || cs != `ISO-8859-1`)=> mediaType.value + "; charset=" + cs.value
    case _ => mediaType.value
  }

  override def equals(obj: Any) = obj match {
    case x: ContentType => mediaType == x.mediaType && charset == x.charset
    case _ => false
  }
}

object ContentType {
  def apply(mediaType: MediaType, charset: HttpCharset): ContentType = apply(mediaType, Some(charset))
  def apply(mediaType: MediaType): ContentType = apply(mediaType, None)
  
  implicit def fromMimeType(mimeType: MediaType): ContentType = apply(mimeType) 
}                     