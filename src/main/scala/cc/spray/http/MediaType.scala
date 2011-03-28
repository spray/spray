/*
 * Copyright (C) 2011 Mathias Doenitz
 * Heavily inspired by the "blueeyes" framework (http://github.com/jdegoes/blueeyes)
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

import cc.spray.utils.ObjectRegistry

sealed trait MediaRange {
  val value = mainType + "/*"
  def mainType: String
  
  def matches(mediaType: MediaType): Boolean = mediaType.mainType == mainType
  
  def isApplication = mainType == "application"
  def isAudio = mainType == "audio"
  def isImage = mainType == "image"
  def isMessage = mainType == "message"
  def isMultipart = mainType == "multipart"
  def isText = mainType == "text"
  def isVideo = mainType == "video"
  
  override def toString = "MediaRange(" + value + ')'
}

object MediaRanges extends ObjectRegistry[String, MediaRange] {
  
  class PredefinedMediaRange private[MediaRanges](val mainType: String) extends MediaRange {
    MediaRanges.register(this, mainType)
  }
  
  val `*/*` = new PredefinedMediaRange("*") {
    override def matches(mediaType: MediaType) = true
  }
  val `application/*` = new PredefinedMediaRange("application")
  val `audio/*`       = new PredefinedMediaRange("audio")
  val `image/*`       = new PredefinedMediaRange("image")
  val `message/*`     = new PredefinedMediaRange("message")
  val `multipart/*`   = new PredefinedMediaRange("multipart")
  val `text/*`        = new PredefinedMediaRange("text")
  val `video/*`       = new PredefinedMediaRange("video")
  
  case class CustomMediaRange(mainType: String) extends MediaRange
}

sealed trait MediaType extends MediaRange {
  lazy val mainType = value.split('/')(0)
  lazy val subType = value.split('/')(1)
  def fileExtensions: Seq[String]

  override def matches(mediaType: MediaType) = this == mediaType

  override def equals(obj: Any) = obj match {
    case x: MediaType => (this eq x) || value == x.value
    case _ => false
  }
  
  override def hashCode() = value.##
  override def toString = "MediaType(" + value + ')'
}

object MediaType {
  def unapply(mimeType: MediaType): Option[(String, String)] = Some(mimeType.mainType, mimeType.subType)
}

object MediaTypes extends ObjectRegistry[String, MediaType] {
  
  def forExtension(ext: String): Option[MediaType] = {
    val extLower = ext.toLowerCase
    registry.values.find(_.fileExtensions.contains(extLower))
  }
  
  class PredefinedMediaType private[MediaTypes](override val value: String, val fileExtensions: String*)
          extends MediaType {
    MediaTypes.register(this, value)
  }
  
  val `application/atom+xml`              = new PredefinedMediaType("application/atom+xml")
  val `application/javascript`            = new PredefinedMediaType("application/javascript", "js")
  val `application/json`                  = new PredefinedMediaType("application/json", "json")
  val `application/octet-stream`          = new PredefinedMediaType("application/octet-stream", "bin", "class", "exe")
  val `application/ogg`                   = new PredefinedMediaType("application/ogg", "ogg")
  val `application/pdf`                   = new PredefinedMediaType("application/pdf", "pdf")
  val `application/postscript`            = new PredefinedMediaType("application/postscript", "ps", "ai")
  val `application/soap+xml`              = new PredefinedMediaType("application/soap+xml")
  val `application/xhtml+xml`             = new PredefinedMediaType("application/xhtml+xml")
  val `application/xml-dtd`               = new PredefinedMediaType("application/xml-dtd")
  val `application/x-javascript`          = new PredefinedMediaType("application/x-javascript", "js")
  val `application/x-shockwave-flash`     = new PredefinedMediaType("application/x-shockwave-flash", "swf")
  val `application/x-www-form-urlencoded` = new PredefinedMediaType("application/x-www-form-urlencoded")
  val `application/zip`                   = new PredefinedMediaType("application/zip", "zip")
                                             
  val `audio/basic`                       = new PredefinedMediaType("audio/basic", "au", "snd")
  val `audio/mp4`                         = new PredefinedMediaType("audio/mp4", "mp4")
  val `audio/mpeg`                        = new PredefinedMediaType("audio/mpeg", "mpg", "mpeg", "mpga", "mpe", "mp3", "mp2")
  val `audio/ogg`                         = new PredefinedMediaType("audio/ogg", "ogg")
  val `audio/vorbis`                      = new PredefinedMediaType("audio/vorbis", "vorbis")
                                             
  val `image/gif`                         = new PredefinedMediaType("image/gif", "gif")
  val `image/png`                         = new PredefinedMediaType("image/png", "png")
  val `image/jpeg`                        = new PredefinedMediaType("image/jpeg", "jpg", "jpeg", "jpe")
  val `image/svg+xml`                     = new PredefinedMediaType("image/svg+xml", "svg")
  val `image/tiff`                        = new PredefinedMediaType("image/tiff", "tif", "tiff")
                                             
  val `message/http`                      = new PredefinedMediaType("message/http")
  val `message/delivery-status`           = new PredefinedMediaType("message/delivery-status")
                                             
  val `multipart/mixed`                   = new PredefinedMediaType("multipart/mixed")
  val `multipart/alternative`             = new PredefinedMediaType("multipart/alternative")
  val `multipart/related`                 = new PredefinedMediaType("multipart/related")
  val `multipart/form-data`               = new PredefinedMediaType("multipart/form-data")
  val `multipart/signed`                  = new PredefinedMediaType("multipart/signed")
  val `multipart/encrypted`               = new PredefinedMediaType("multipart/encrypted")
                                             
  val `text/css`                          = new PredefinedMediaType("text/css", "css")
  val `text/csv`                          = new PredefinedMediaType("text/csv", "csv")
  val `text/html`                         = new PredefinedMediaType("text/html", "html", "htm")
  val `text/javascript`                   = new PredefinedMediaType("text/javascript", "js")
  val `text/plain`                        = new PredefinedMediaType("text/plain", "txt", "text", "conf", "properties")
  val `text/xml`                          = new PredefinedMediaType("text/xml", "xml")
                                             
  val `video/mpeg`                        = new PredefinedMediaType("video/mpeg", "mpg", "mpeg")
  val `video/mp4`                         = new PredefinedMediaType("video/mp4", "mp4")
  val `video/ogg`                         = new PredefinedMediaType("video/ogg", "ogg")
  val `video/quicktime`                   = new PredefinedMediaType("video/quicktime", "qt", "mov")
  
  case class CustomMediaType(override val value: String, fileExtensions: String*) extends MediaType
}