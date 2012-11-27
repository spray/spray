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

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec


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
    register(mediaRange.mainType.toLowerCase, mediaRange)
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

  private[this] val extensionMap = new AtomicReference(Map.empty[String, MediaType])

  @tailrec
  private def registerFileExtension(ext: String, mediaType: MediaType) {
    val lcExt = ext.toLowerCase
    val current = extensionMap.get
    require(!current.contains(lcExt), "Extension '%s' clash: media-types '%s' and '%s'" format (ext, current(lcExt), mediaType))
    val updated = current.updated(lcExt, mediaType)
    if (!extensionMap.compareAndSet(current, updated)) registerFileExtension(ext, mediaType)
  }

  def register(mediaType: MediaType): MediaType = {
    register(mediaType.mainType.toLowerCase -> mediaType.subType.toLowerCase, mediaType)
    mediaType.fileExtensions.foreach(registerFileExtension(_, mediaType))
    mediaType
  }

  def forExtension(ext: String): Option[MediaType] = extensionMap.get.get(ext.toLowerCase)

  private[this] def app(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "application"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isApplication = true
    }
  }

  private[this] def aud(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "audio"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isAudio = true
    }
  }

  private[this] def img(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "image"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isImage = true
    }
  }

  private[this] def msg(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "message"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isMessage = true
    }
  }

  private[this] def txt(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "text"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isText = true
    }
  }

  private[this] def vid(_subType: String, _fileExtensions: String*) = register {
    new MediaType {
      def mainType = "video"
      def subType = _subType
      def fileExtensions = _fileExtensions
      override def isVideo = true
    }
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
  case class CustomMediaType(mainType: String, subType: String, fileExtensions: Seq[String] = Nil) extends MediaType {
    override def isApplication = mainType == "application"
    override def isAudio       = mainType == "audio"
    override def isImage       = mainType == "image"
    override def isMessage     = mainType == "message"
    override def isMultipart   = mainType == "multipart"
    override def isText        = mainType == "text"
    override def isVideo       = mainType == "video"
  }

  val `application/atom+xml`                                                      = app("atom+xml", "atom")
  val `application/base64`                                                        = app("base64", "mm", "mme")
  val `application/excel`                                                         = app("excel", "xl", "xla", "xlb", "xlc", "xld", "xlk", "xll", "xlm", "xls", "xlt", "xlv", "xlw")
  val `application/font-woff`                                                     = app("font-woff", "woff")
  val `application/gnutar`                                                        = app("gnutar", "tgz")
  val `application/java-archive`                                                  = app("javascript", "jar", "war", "ear")
  val `application/javascript`                                                    = app("javascript", "js")
  val `application/json`                                                          = app("json", "json")
  val `application/lha`                                                           = app("lha", "lha")
  val `application/lzx`                                                           = app("lzx", "lzx")
  val `application/mspowerpoint`                                                  = app("mspowerpoint", "pot", "pps", "ppt", "ppz")
  val `application/msword`                                                        = app("msword", "doc", "dot", "w6w", "wiz", "word", "wri")
  val `application/octet-stream`                                                  = app("octet-stream", "a", "bin", "class", "dump", "exe", "lhx", "lzh", "o", "psd", "saveme", "zoo")
  val `application/pdf`                                                           = app("pdf", "pdf")
  val `application/postscript`                                                    = app("postscript", "ai", "eps", "ps")
  val `application/rss+xml`                                                       = app("rss+xml", "rss")
  val `application/soap+xml`                                                      = app("soap+xml")
  val `application/vnd.google-earth.kml+xml`                                      = app("vnd.google-earth.kml+xml", "kml")
  val `application/vnd.google-earth.kmz`                                          = app("vnd.google-earth.kmz", "kmz")
  val `application/vnd.ms-fontobject`                                             = app("vnd.ms-fontobject", "eot")
  val `application/vnd.oasis.opendocument.chart`                                  = app("vnd.oasis.opendocument.chart", "odc")
  val `application/vnd.oasis.opendocument.database`                               = app("vnd.oasis.opendocument.database", "odb")
  val `application/vnd.oasis.opendocument.formula`                                = app("vnd.oasis.opendocument.formula", "odf")
  val `application/vnd.oasis.opendocument.graphics`                               = app("vnd.oasis.opendocument.graphics", "odg")
  val `application/vnd.oasis.opendocument.image`                                  = app("vnd.oasis.opendocument.image", "odi")
  val `application/vnd.oasis.opendocument.presentation`                           = app("vnd.oasis.opendocument.presentation", "odp")
  val `application/vnd.oasis.opendocument.spreadsheet`                            = app("vnd.oasis.opendocument.spreadsheet", "ods")
  val `application/vnd.oasis.opendocument.text`                                   = app("vnd.oasis.opendocument.text", "odt")
  val `application/vnd.oasis.opendocument.text-master`                            = app("vnd.oasis.opendocument.text-master", "odm", "otm")
  val `application/vnd.oasis.opendocument.text-web`                               = app("vnd.oasis.opendocument.text-web", "oth")
  val `application/vnd.openxmlformats-officedocument.presentationml.presentation` = app("vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
  val `application/vnd.openxmlformats-officedocument.presentationml.slide`        = app("vnd.openxmlformats-officedocument.presentationml.slide", "sldx")
  val `application/vnd.openxmlformats-officedocument.presentationml.slideshow`    = app("vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx")
  val `application/vnd.openxmlformats-officedocument.presentationml.template`     = app("vnd.openxmlformats-officedocument.presentationml.template", "potx")
  val `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`         = app("vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
  val `application/vnd.openxmlformats-officedocument.spreadsheetml.template`      = app("vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx")
  val `application/vnd.openxmlformats-officedocument.wordprocessingml.document`   = app("vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
  val `application/vnd.openxmlformats-officedocument.wordprocessingml.template`   = app("vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx")
  val `application/x-7z-compressed`                                               = app("x-7z-compressed", "7z", "s7z")
  val `application/x-ace-compressed`                                              = app("x-ace-compressed", "ace")
  val `application/x-apple-diskimage`                                             = app("x-apple-diskimage", "dmg")
  val `application/x-arc-compressed`                                              = app("x-arc-compressed", "arc")
  val `application/x-bzip`                                                        = app("x-bzip", "bz")
  val `application/x-bzip2`                                                       = app("x-bzip2", "boz", "bz2")
  val `application/x-chrome-extension`                                            = app("x-chrome-extension", "crx")
  val `application/x-compress`                                                    = app("x-compress", "z")
  val `application/x-compressed`                                                  = app("x-compressed", "gz")
  val `application/x-debian-package`                                              = app("x-debian-package", "deb")
  val `application/x-dvi`                                                         = app("x-dvi", "dvi")
  val `application/x-font-truetype`                                               = app("x-font-truetype", "ttf")
  val `application/x-font-opentype`                                               = app("x-font-opentype", "otf")
  val `application/x-gtar`                                                        = app("x-gtar", "gtar")
  val `application/x-gzip`                                                        = app("x-gzip", "gzip")
  val `application/x-latex`                                                       = app("x-latex", "latex", "ltx")
  val `application/x-rar-compressed`                                              = app("x-rar-compressed", "rar")
  val `application/x-redhat-package-manager`                                      = app("x-redhat-package-manager", "rpm")
  val `application/x-shockwave-flash`                                             = app("x-shockwave-flash", "swf")
  val `application/x-tar`                                                         = app("x-tar", "tar")
  val `application/x-tex`                                                         = app("x-tex", "tex")
  val `application/x-texinfo`                                                     = app("x-texinfo", "texi", "texinfo")
  val `application/x-vrml`                                                        = app("x-vrml", "vrml")
  val `application/x-www-form-urlencoded`                                         = app("x-www-form-urlencoded")
  val `application/x-x509-ca-cert`                                                = app("x-x509-ca-cert", "der")
  val `application/x-xpinstall`                                                   = app("x-xpinstall", "xpi")
  val `application/xhtml+xml`                                                     = app("xhtml+xml")
  val `application/xml-dtd`                                                       = app("xml-dtd")
  val `application/zip`                                                           = app("zip", "zip")

  val `audio/aiff`        = aud("aiff", "aif", "aifc", "aiff")
  val `audio/basic`       = aud("basic", "au", "snd")
  val `audio/midi`        = aud("midi", "mid", "midi", "kar")
  val `audio/mod`         = aud("mod", "mod")
  val `audio/mpeg`        = aud("mpeg", "m2a", "mp2", "mp3", "mpa", "mpga")
  val `audio/ogg`         = aud("ogg", "oga", "ogg")
  val `audio/voc`         = aud("voc", "voc")
  val `audio/vorbis`      = aud("vorbis", "vorbis")
  val `audio/voxware`     = aud("voxware", "vox")
  val `audio/wav`         = aud("wav", "wav")
  val `audio/x-realaudio` = aud("x-pn-realaudio", "ra", "ram", "rmm", "rmp")
  val `audio/x-psid`      = aud("x-psid", "sid")
  val `audio/xm`          = aud("xm", "xm")

  val `image/gif`         = img("gif", "gif")
  val `image/jpeg`        = img("jpeg", "jpe", "jpeg", "jpg")
  val `image/pict`        = img("pict", "pic", "pict")
  val `image/png`         = img("png", "png")
  val `image/svg+xml`     = img("svg+xml", "svg", "svgz")
  val `image/tiff`        = img("tiff", "tif", "tiff")
  val `image/x-icon`      = img("x-icon", "ico")
  val `image/x-ms-bmp`    = img("x-ms-bmp", "bmp")
  val `image/x-pcx`       = img("x-pcx", "pcx")
  val `image/x-pict`      = img("x-pict", "pct")
  val `image/x-quicktime` = img("x-quicktime", "qif", "qti", "qtif")
  val `image/x-rgb`       = img("x-rgb", "rgb")
  val `image/x-xbitmap`   = img("x-xbitmap", "xbm")
  val `image/x-xpixmap`   = img("x-xpixmap", "xpm")

  val `message/http`            = msg("http")
  val `message/delivery-status` = msg("delivery-status")
  val `message/rfc822`          = msg("rfc822", "eml", "mht", "mhtml", "mime")

  class `multipart/mixed`      (boundary: Option[String]) extends MultipartMediaType("mixed", boundary)
  class `multipart/alternative`(boundary: Option[String]) extends MultipartMediaType("alternative", boundary)
  class `multipart/related`    (boundary: Option[String]) extends MultipartMediaType("related", boundary)
  class `multipart/form-data`  (boundary: Option[String]) extends MultipartMediaType("form-data", boundary)
  class `multipart/signed`     (boundary: Option[String]) extends MultipartMediaType("signed", boundary)
  class `multipart/encrypted`  (boundary: Option[String]) extends MultipartMediaType("encrypted", boundary)

  val `multipart/mixed`       = new `multipart/mixed`(None)
  val `multipart/alternative` = new `multipart/alternative`(None)
  val `multipart/related`     = new `multipart/related`(None)
  val `multipart/form-data`   = new `multipart/form-data`(None)
  val `multipart/signed`      = new `multipart/signed`(None)
  val `multipart/encrypted`   = new `multipart/encrypted`(None)

  val `text/asp`                  = txt("asp", "asp")
  val `text/cache-manifest`       = txt("cache-manifest", "manifest")
  val `text/calendar`             = txt("calendar", "ics", "icz")
  val `text/css`                  = txt("css", "css")
  val `text/csv`                  = txt("csv", "csv")
  val `text/html`                 = txt("html", "htm", "html", "htmls", "htx")
  val `text/mcf`                  = txt("mcf", "mcf")
  val `text/plain`                = txt("plain", "conf", "text", "txt", "properties")
  val `text/richtext`             = txt("richtext", "rtf", "rtx")
  val `text/tab-separated-values` = txt("tab-separated-values", "tsv")
  val `text/uri-list`             = txt("uri-list", "uni", "unis", "uri", "uris")
  val `text/vnd.wap.wml`          = txt("vnd.wap.wml", "wml")
  val `text/vnd.wap.wmlscript`    = txt("vnd.wap.wmlscript", "wmls")
  val `text/x-asm`                = txt("x-asm", "asm", "s")
  val `text/x-c`                  = txt("x-c", "c", "cc", "cpp")
  val `text/x-component`          = txt("x-component", "htc")
  val `text/x-h`                  = txt("x-h", "h", "hh")
  val `text/x-java-source`        = txt("x-java-source", "jav", "java")
  val `text/x-pascal`             = txt("x-pascal", "p")
  val `text/x-script`             = txt("x-script", "hlb")
  val `text/x-scriptcsh`          = txt("x-scriptcsh", "csh")
  val `text/x-scriptelisp`        = txt("x-scriptelisp", "el")
  val `text/x-scriptksh`          = txt("x-scriptksh", "ksh")
  val `text/x-scriptlisp`         = txt("x-scriptlisp", "lsp")
  val `text/x-scriptperl`         = txt("x-scriptperl", "pl")
  val `text/x-scriptperl-module`  = txt("x-scriptperl-module", "pm")
  val `text/x-scriptphyton`       = txt("x-scriptphyton", "py")
  val `text/x-scriptrexx`         = txt("x-scriptrexx", "rexx")
  val `text/x-scriptscheme`       = txt("x-scriptscheme", "scm")
  val `text/x-scriptsh`           = txt("x-scriptsh", "sh")
  val `text/x-scripttcl`          = txt("x-scripttcl", "tcl")
  val `text/x-scripttcsh`         = txt("x-scripttcsh", "tcsh")
  val `text/x-scriptzsh`          = txt("x-scriptzsh", "zsh")
  val `text/x-server-parsed-html` = txt("x-server-parsed-html", "shtml", "ssi")
  val `text/x-setext`             = txt("x-setext", "etx")
  val `text/x-sgml`               = txt("x-sgml", "sgm", "sgml")
  val `text/x-speech`             = txt("x-speech", "spc", "talk")
  val `text/x-uuencode`           = txt("x-uuencode", "uu", "uue")
  val `text/x-vcalendar`          = txt("x-vcalendar", "vcs")
  val `text/x-vcard`              = txt("x-vcard", "vcf", "vcard")
  val `text/xml`                  = txt("xml", "xml")

  val `video/avs-video`     = vid("avs-video", "avs")
  val `video/divx`          = vid("divx", "divx")
  val `video/gl`            = vid("gl", "gl")
  val `video/mp4`           = vid("mp4", "mp4")
  val `video/mpeg`          = vid("mpeg", "m1v", "m2v", "mpe", "mpeg", "mpg")
  val `video/ogg`           = vid("ogg", "ogv")
  val `video/quicktime`     = vid("quicktime", "moov", "mov", "qt")
  val `video/x-dv`          = vid("x-dv", "dif", "dv")
  val `video/x-flv`         = vid("x-flv", "flv")
  val `video/x-motion-jpeg` = vid("x-motion-jpeg", "mjpg")
  val `video/x-ms-asf`      = vid("x-ms-asf", "asf")
  val `video/x-msvideo`     = vid("x-msvideo", "avi")
  val `video/x-sgi-movie`   = vid("x-sgi-movie", "movie", "mv")
}