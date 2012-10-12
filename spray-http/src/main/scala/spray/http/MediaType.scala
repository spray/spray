/*
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

sealed abstract class MediaRange {
  val value = mainType + "/*"
  def mainType: String
  
  def matches(mediaType: MediaType): Boolean
  
  def isApplication = false
  def isAudio       = false
  def isImage       = false
  def isMessage     = false
  def isMultipart   = false
  def isText        = false
  def isVideo       = false
  
  override def toString = "MediaRange(" + value + ')'
}

object MediaRanges extends ObjectRegistry[String, MediaRange] {
  
  def register(mediaRange: MediaRange): MediaRange = {
    register(mediaRange, mediaRange.mainType.toLowerCase)
    mediaRange
  }
  
  val `*/*` = register {
    new MediaRange {
      def mainType = "*"
      def matches(mediaType: MediaType) = true
    }
  }
  val `application/*` = register {
    new MediaRange {
      def mainType = "application"
      def matches(mediaType: MediaType) = mediaType.isApplication
      override def isApplication = true
    }
  }
  val `audio/*` = register {
    new MediaRange {
      def mainType = "audio"
      def matches(mediaType: MediaType) = mediaType.isAudio
      override def isAudio = true
    }
  }
  val `image/*` = register {
    new MediaRange {
      def mainType = "image"
      def matches(mediaType: MediaType) = mediaType.isImage
      override def isImage = true
    }
  }
  val `message/*` = register {
    new MediaRange {
      def mainType = "message"
      def matches(mediaType: MediaType) = mediaType.isMessage
      override def isMessage = true
    }
  }
  val `multipart/*` = register {
    new MediaRange {
      def mainType = "multipart"
      def matches(mediaType: MediaType) = mediaType.isMultipart
      override def isMultipart = true
    }
  }
  val `text/*` = register {
    new MediaRange {
      def mainType = "text"
      def matches(mediaType: MediaType) = mediaType.isText
      override def isText = true
    }
  }
  val `video/*` = register {
    new MediaRange {
      def mainType = "video"
      def matches(mediaType: MediaType) = mediaType.isVideo
      override def isVideo = true
    }
  }
  
  case class CustomMediaRange(mainType: String) extends MediaRange {
    def matches(mediaType: MediaType) = mediaType.mainType == mainType
    override def isApplication = mainType == "application"
    override def isAudio       = mainType == "audio"
    override def isImage       = mainType == "image"
    override def isMessage     = mainType == "message"
    override def isMultipart   = mainType == "multipart"
    override def isText        = mainType == "text"
    override def isVideo       = mainType == "video"
  }
}

sealed abstract class MediaType extends MediaRange {
  override val value = mainType + '/' + subType
  def mainType: String
  def subType: String
  def fileExtensions: Seq[String]

  override def matches(mediaType: MediaType) = this == mediaType

  override def equals(obj: Any) = obj match {
    case x: MediaType => (this eq x) || mainType == x.mainType && subType == x.subType
    case _ => false
  }

  override def hashCode() = value.##
  override def toString = "MediaType(" + value + ')'
}

object MediaType {
  def unapply(mimeType: MediaType): Option[(String, String)] = Some(mimeType.mainType, mimeType.subType)
}

object MediaTypes extends ObjectRegistry[(String, String), MediaType] {
  
  def register(mediaType: MediaType): MediaType = {
    register(mediaType, mediaType.mainType.toLowerCase -> mediaType.subType.toLowerCase)
    mediaType
  }
  
  def forExtension(ext: String): Option[MediaType] = {
    val extLower = ext.toLowerCase
    registry.values.find(_.fileExtensions.contains(extLower))
  }
  
  class ApplicationMediaType(val subType: String, val fileExtensions: String*) extends MediaType {
    def mainType = "application"
    override def isApplication = true
  }
  class AudioMediaType(val subType: String, val fileExtensions: String*) extends MediaType {
    def mainType = "audio"
    override def isAudio = true
  }
  class ImageMediaType(val subType: String, val fileExtensions: String*) extends MediaType {
    def mainType = "image"
    override def isImage = true
  }
  class MessageMediaType(val subType: String) extends MediaType {
    def mainType = "message"
    def fileExtensions = Nil
    override def isMessage = true
  }
  class MultipartMediaType(val subType: String, val boundary: Option[String]) extends MediaType {
    override val value = boundary match {
      case None       => mainType + '/' + subType
      case _: Some[_] => mainType + '/' + subType + "; boundary=\"" + boundary.get + '"'
    }
    def mainType = "multipart"
    def fileExtensions = Nil
    override def isMultipart = true
  }
  class TextMediaType(val subType: String, val fileExtensions: String*) extends MediaType {
    def mainType = "text"
    override def isText = true
  }
  class VideoMediaType(val subType: String, val fileExtensions: String*) extends MediaType {
    def mainType = "video"
    override def isVideo = true
  }
  
  val `application/atom+xml`              = register(new ApplicationMediaType("atom+xml"))
  val `application/javascript`            = register(new ApplicationMediaType("javascript", "js"))
  val `application/json`                  = register(new ApplicationMediaType("json", "json"))
  val `application/octet-stream`          = register(new ApplicationMediaType("octet-stream", "bin", "class", "exe"))
  val `application/ogg`                   = register(new ApplicationMediaType("ogg", "ogg"))
  val `application/pdf`                   = register(new ApplicationMediaType("pdf", "pdf"))
  val `application/postscript`            = register(new ApplicationMediaType("postscript", "ps", "ai"))
  val `application/soap+xml`              = register(new ApplicationMediaType("soap+xml"))
  val `application/xhtml+xml`             = register(new ApplicationMediaType("xhtml+xml"))
  val `application/xml-dtd`               = register(new ApplicationMediaType("xml-dtd"))
  val `application/x-javascript`          = register(new ApplicationMediaType("x-javascript", "js"))
  val `application/x-shockwave-flash`     = register(new ApplicationMediaType("x-shockwave-flash", "swf"))
  val `application/x-www-form-urlencoded` = register(new ApplicationMediaType("x-www-form-urlencoded"))
  val `application/zip`                   = register(new ApplicationMediaType("zip", "zip"))
                                             
  val `audio/basic`                       = register(new AudioMediaType("basic", "au", "snd"))
  val `audio/midi`                        = register(new AudioMediaType("midi", "mid", "kar"))
  val `audio/mp4`                         = register(new AudioMediaType("mp4", "mp4"))
  val `audio/mpeg`                        = register(new AudioMediaType("mpeg", "mpg", "mpeg", "mpga", "mpe", "mp3", "mp2"))
  val `audio/ogg`                         = register(new AudioMediaType("ogg", "ogg"))
  val `audio/vorbis`                      = register(new AudioMediaType("vorbis", "vorbis"))
                                             
  val `image/gif`                         = register(new ImageMediaType("gif", "gif"))
  val `image/png`                         = register(new ImageMediaType("png", "png"))
  val `image/jpeg`                        = register(new ImageMediaType("jpeg", "jpg", "jpeg", "jpe"))
  val `image/svg+xml`                     = register(new ImageMediaType("svg+xml", "svg"))
  val `image/tiff`                        = register(new ImageMediaType("tiff", "tif", "tiff"))
  val `image/vnd.microsoft.icon`          = register(new ImageMediaType("vnd.microsoft.icon", "ico"))
                                             
  val `message/http`                      = register(new MessageMediaType("http"))
  val `message/delivery-status`           = register(new MessageMediaType("delivery-status"))
                                             
  class `multipart/mixed`      (boundary: Option[String]) extends MultipartMediaType("mixed", boundary)
  class `multipart/alternative`(boundary: Option[String]) extends MultipartMediaType("alternative", boundary)
  class `multipart/related`    (boundary: Option[String]) extends MultipartMediaType("related", boundary)
  class `multipart/form-data`  (boundary: Option[String]) extends MultipartMediaType("form-data", boundary)
  class `multipart/signed`     (boundary: Option[String]) extends MultipartMediaType("signed", boundary)
  class `multipart/encrypted`  (boundary: Option[String]) extends MultipartMediaType("encrypted", boundary)
  val `multipart/mixed`                   = new `multipart/mixed`(None)
  val `multipart/alternative`             = new `multipart/alternative`(None)
  val `multipart/related`                 = new `multipart/related`(None)
  val `multipart/form-data`               = new `multipart/form-data`(None)
  val `multipart/signed`                  = new `multipart/signed`(None)
  val `multipart/encrypted`               = new `multipart/encrypted`(None)

  val `text/css`                          = register(new TextMediaType("css", "css"))
  val `text/csv`                          = register(new TextMediaType("csv", "csv"))
  val `text/html`                         = register(new TextMediaType("html", "html", "htm"))
  val `text/javascript`                   = register(new TextMediaType("javascript", "js"))
  val `text/plain`                        = register(new TextMediaType("plain", "txt", "text", "conf", "properties"))
  val `text/xml`                          = register(new TextMediaType("xml", "xml"))
                                             
  val `video/mpeg`                        = register(new VideoMediaType("mpeg", "mpg", "mpeg"))
  val `video/mp4`                         = register(new VideoMediaType("mp4", "mp4"))
  val `video/ogg`                         = register(new VideoMediaType("ogg", "ogg"))
  val `video/quicktime`                   = register(new VideoMediaType("quicktime", "qt", "mov"))

  object CustomMediaType {
    def apply(value: String, fileExtensions: String*) = {
      val parts = value.split('/')
      if (parts.length != 2) throw new IllegalArgumentException(value + " is not a valid media-type")
      new CustomMediaType(parts(0), parts(1), fileExtensions)
    }
  }

  /**
   * Allows the definition of custom media types. In order for your custom type to be properly used by the
   * HTTP layer you need to create an instance, register it via `MediaTypes.register` and use this instance in
   * your custom Marshallers and Unmarshallers.
   */
  case class CustomMediaType(mainType: String, subType: String, fileExtensions: Seq[String] = Nil)
          extends MediaType {
    override def isApplication = mainType == "application"
    override def isAudio       = mainType == "audio"
    override def isImage       = mainType == "image"
    override def isMessage     = mainType == "message"
    override def isMultipart   = mainType == "multipart"
    override def isText        = mainType == "text"
    override def isVideo       = mainType == "video"
  }
}