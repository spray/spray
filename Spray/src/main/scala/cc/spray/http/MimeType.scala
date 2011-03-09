package cc.spray.http

import cc.spray.utils.ObjectRegistry

sealed trait MimeType {
  def value: String
  lazy val mainType = value.split("/")(0)
  lazy val subType = value.split("/")(1)
  def fileExtensions: Seq[String]
  def defaultExtension = fileExtensions.headOption
  override def toString = value
  
  def matchesOrIncludes(other: MimeType) = (this eq other) || (subType == "+" && mainType == other.mainType)
  
  MimeTypes.register(this, value)
}

object MimeTypes extends ObjectRegistry[String, MimeType] {
  
  def forExtension(ext: String): Option[MimeType] = {
    val extLower = ext.toLowerCase
    registry.values.find(_.fileExtensions.contains(extLower))
  }
  
  class PredefinedMimeType private[MimeTypes](val value: String, val fileExtensions: String*) extends MimeType
  
  val `+/+` = new PredefinedMimeType("+/+") 
  
  val `application/+` = new PredefinedMimeType("application/+")   
  val `audio/+`       = new PredefinedMimeType("audio/+")            
  val `image/+`       = new PredefinedMimeType("image/+")            
  val `message/+`     = new PredefinedMimeType("message/+")    
  val `multipart/+`   = new PredefinedMimeType("multipart/+")  
  val `text/+`        = new PredefinedMimeType("text/+")       
  val `video/+`       = new PredefinedMimeType("video/+")
  
  val `application/atom+xml`              = new PredefinedMimeType("application/atom+xml")
  val `application/javascript`            = new PredefinedMimeType("application/javascript", "js")
  val `application/json`                  = new PredefinedMimeType("application/json", "json")
  val `application/octet-stream`          = new PredefinedMimeType("application/octet-stream", "bin", "class", "exe")
  val `application/ogg`                   = new PredefinedMimeType("application/ogg", "ogg")
  val `application/pdf`                   = new PredefinedMimeType("application/pdf", "pdf")
  val `application/postscript`            = new PredefinedMimeType("application/postscript", "ps", "ai")
  val `application/soap+xml`              = new PredefinedMimeType("application/soap+xml")
  val `application/xhtml+xml`             = new PredefinedMimeType("application/xhtml+xml")
  val `application/xml-dtd`               = new PredefinedMimeType("application/xml-dtd")
  val `application/x-javascript`          = new PredefinedMimeType("application/x-javascript", "js")
  val `application/x-shockwave-flash`     = new PredefinedMimeType("application/x-shockwave-flash", "swf")
  val `application/x-www-form-urlencoded` = new PredefinedMimeType("application/x-www-form-urlencoded")
  val `application/zip`                   = new PredefinedMimeType("application/zip", "zip")
  
  val `audio/basic`   = new PredefinedMimeType("audio/basic", "au", "snd")
  val `audio/mp4`     = new PredefinedMimeType("audio/mp4", "mp4")
  val `audio/mpeg`    = new PredefinedMimeType("audio/mpeg", "mpg", "mpeg", "mpga", "mpe", "mp3", "mp2")
  val `audio/ogg`     = new PredefinedMimeType("audio/ogg", "ogg")
  val `audio/vorbis`  = new PredefinedMimeType("audio/vorbis", "vorbis")
  
  val `image/gif`     = new PredefinedMimeType("image/gif", "gif")
  val `image/png`     = new PredefinedMimeType("image/png", "png")
  val `image/jpeg`    = new PredefinedMimeType("image/jpeg", "jpg", "jpeg", "jpe")
  val `image/svg+xml` = new PredefinedMimeType("image/svg+xml", "svg")
  val `image/tiff`    = new PredefinedMimeType("image/tiff", "tif", "tiff")
  
  val `message/http`            = new PredefinedMimeType("message/http")
  val `message/delivery-status` = new PredefinedMimeType("message/delivery-status")
  
  val `multipart/mixed`       = new PredefinedMimeType("multipart/mixed")
  val `multipart/alternative` = new PredefinedMimeType("multipart/alternative")
  val `multipart/related`     = new PredefinedMimeType("multipart/related")
  val `multipart/form-data`   = new PredefinedMimeType("multipart/form-data")
  val `multipart/signed`      = new PredefinedMimeType("multipart/signed")
  val `multipart/encrypted`   = new PredefinedMimeType("multipart/encrypted")
  
  val `text/css`        = new PredefinedMimeType("text/css", "css")
  val `text/csv`        = new PredefinedMimeType("text/csv", "csv")
  val `text/html`       = new PredefinedMimeType("text/html", "html", "htm")
  val `text/javascript` = new PredefinedMimeType("text/javascript", "js")
  val `text/plain`      = new PredefinedMimeType("text/plain", "txt", "text", "conf", "properties")
  val `text/xml`        = new PredefinedMimeType("text/xml", "xml")
  
  val `video/mpeg`      = new PredefinedMimeType("video/mpeg", "mpg", "mpeg")
  val `video/mp4`       = new PredefinedMimeType("video/mp4", "mp4")
  val `video/ogg`       = new PredefinedMimeType("video/ogg", "ogg")
  val `video/quicktime` = new PredefinedMimeType("video/quicktime", "qt", "mov")
  
  case class CustomMimeType(override val value: String) extends MimeType {
    override def matchesOrIncludes(other: MimeType) = this == other
    def fileExtensions = Nil
  }
}