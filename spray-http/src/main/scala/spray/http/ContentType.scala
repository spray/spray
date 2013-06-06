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

sealed abstract class ContentTypeRange extends ValueRenderable {
  def mediaRange: MediaRange
  def charsetRange: HttpCharsetRange
  def matches(contentType: ContentType) =
    mediaRange.matches(contentType.mediaType) && charsetRange.matches(contentType.charset)
}

object ContentTypeRange {
  private case class Impl(mediaRange: MediaRange, charsetRange: HttpCharsetRange) extends ContentTypeRange {
    def render[R <: Rendering](r: R): R = charsetRange match {
      case HttpCharsets.`*` ⇒ r ~~ mediaRange
      case x: HttpCharset   ⇒ r ~~ mediaRange ~~ ContentType.`; charset=` ~~ charsetRange
    }
  }

  implicit def apply(mediaRange: MediaRange): ContentTypeRange = apply(mediaRange, HttpCharsets.`*`)
  def apply(mediaRange: MediaRange, charsetRange: HttpCharsetRange): ContentTypeRange = Impl(mediaRange, charsetRange)
}

case class ContentType(mediaType: MediaType, definedCharset: Option[HttpCharset]) extends ContentTypeRange {
  def render[R <: Rendering](r: R): R = definedCharset match {
    case Some(cs) ⇒ r ~~ mediaType ~~ ContentType.`; charset=` ~~ cs
    case _        ⇒ r ~~ mediaType
  }
  def charset: HttpCharset = definedCharset getOrElse HttpCharsets.`ISO-8859-1`
  def mediaRange: MediaRange = mediaType
  def charsetRange: HttpCharsetRange = charset

  def isCharsetDefined = definedCharset.isDefined
  def noCharsetDefined = definedCharset.isEmpty

  def withMediaType(mediaType: MediaType) =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: HttpCharset) =
    if (noCharsetDefined || charset != definedCharset.get) copy(definedCharset = Some(charset)) else this
  def withoutDefinedCharset =
    if (isCharsetDefined) copy(definedCharset = None) else this
}

object ContentType {
  private[http] case object `; charset=` extends SingletonValueRenderable

  def apply(mediaType: MediaType, charset: HttpCharset): ContentType = apply(mediaType, Some(charset))
  implicit def apply(mediaType: MediaType): ContentType = apply(mediaType, None)
}

object ContentTypes {
  val `*` = ContentTypeRange(MediaRanges.`*/*`, HttpCharsets.`*`)

  // RFC4627 defines JSON to always be UTF encoded, we always render JSON to UTF-8
  val `application/json` = ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`)
  val `text/plain` = ContentType(MediaTypes.`text/plain`)
  val `application/octet-stream` = ContentType(MediaTypes.`application/octet-stream`)
}
