package cc.spray.http

import cc.spray.utils.ObjectRegistry

sealed trait MimeType {
  def value: String
  lazy val maintype = value.split("/")(0)
  lazy val subtype = value.split("/")(1)
  def fileExtensions: Seq[String]
  def defaultExtension = fileExtensions.headOption
  override def toString = value
  
  MimeObjects.register(this, value)
}

object MimeTypes {
  class `+/+` protected[http] (val fileExtensions: String*) extends MimeType {
    def value = "+/+"
  }
  
  class `application/+` protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `audio/+`       protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `image/+`       protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `message/+`     protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `multipart/+`   protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `text/+`        protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  class `video/+`       protected[http] (override val value: String, fileExtensions: String*) extends `+/+`(fileExtensions: _*)
  
  class `application/atom+xml`              private[http]() extends `application/+`("application/atom+xml")
  class `application/javascript`            private[http]() extends `application/+`("application/javascript", "js")
  class `application/json`                  private[http]() extends `application/+`("application/json", "json")
  class `application/octet-stream`          private[http]() extends `application/+`("application/octet-stream", "bin", "class", "exe")
  class `application/ogg`                   private[http]() extends `application/+`("application/ogg", "ogg")
  class `application/pdf`                   private[http]() extends `application/+`("application/pdf", "pdf")
  class `application/postscript`            private[http]() extends `application/+`("application/postscript", "ps", "ai")
  class `application/soap+xml`              private[http]() extends `application/+`("application/soap+xml")
  class `application/xhtml+xml`             private[http]() extends `application/+`("application/xhtml+xml")
  class `application/xml-dtd`               private[http]() extends `application/+`("application/xml-dtd")
  class `application/x-javascript`          private[http]() extends `application/+`("application/x-javascript", "js")
  class `application/x-shockwave-flash`     private[http]() extends `application/+`("application/x-shockwave-flash", "swf")
  class `application/x-www-form-urlencoded` private[http]() extends `application/+`("application/x-www-form-urlencoded")
  class `application/zip`                   private[http]() extends `application/+`("application/zip", "zip")
  
  class `audio/basic`   private[http]() extends `audio/+`("audio/basic", "au", "snd")
  class `audio/mp4`     private[http]() extends `audio/+`("audio/mp4", "mp4")
  class `audio/mpeg`    private[http]() extends `audio/+`("audio/mpeg", "mpg", "mpeg", "mpga", "mpe", "mp3", "mp2")
  class `audio/ogg`     private[http]() extends `audio/+`("audio/ogg", "ogg")
  class `audio/vorbis`  private[http]() extends `audio/+`("audio/vorbis", "vorbis")
  
  class `image/gif`     private[http]() extends `image/+`("image/gif", "gif")
  class `image/png`     private[http]() extends `image/+`("image/png", "png")
  class `image/jpeg`    private[http]() extends `image/+`("image/jpeg", "jpg", "jpeg", "jpe")
  class `image/svg+xml` private[http]() extends `image/+`("image/svg+xml", "svg")
  class `image/tiff`    private[http]() extends `image/+`("image/tiff", "tif", "tiff")
  
  class `message/http`            private[http]() extends `message/+`("message/http")
  class `message/delivery-status` private[http]() extends `message/+`("message/delivery-status")
  
  class `multipart/mixed`       private[http]() extends `multipart/+`("multipart/mixed")
  class `multipart/alternative` private[http]() extends `multipart/+`("multipart/alternative")
  class `multipart/related`     private[http]() extends `multipart/+`("multipart/related")
  class `multipart/form-data`   private[http]() extends `multipart/+`("multipart/form-data")
  class `multipart/signed`      private[http]() extends `multipart/+`("multipart/signed")
  class `multipart/encrypted`   private[http]() extends `multipart/+`("multipart/encrypted")
  
  class `text/css`        private[http]() extends `text/+`("text/css", "css")
  class `text/csv`        private[http]() extends `text/+`("text/csv", "csv")
  class `text/html`       private[http]() extends `text/+`("text/html", "html", "htm")
  class `text/javascript` private[http]() extends `text/+`("text/javascript", "js")
  class `text/plain`      private[http]() extends `text/+`("text/plain", "txt", "text", "conf", "properties")
  class `text/xml`        private[http]() extends `text/+`("text/xml", "xml")
  
  class `video/mpeg`      private[http]() extends `video/+`("video/mpeg", "mpg", "mpeg")
  class `video/mp4`       private[http]() extends `video/+`("video/mp4", "mp4")
  class `video/ogg`       private[http]() extends `video/+`("video/ogg", "ogg")
  class `video/quicktime` private[http]() extends `video/+`("video/quicktime", "qt", "mov")
  
  case class CustomMimeType(override val value: String) extends `+/+`
}

object MimeObjects extends ObjectRegistry[String, MimeType] {
  val `+/+` = new MimeTypes.`+/+`("+/+") 
  
  val `application/+` = new MimeTypes.`application/+`("application/+")   
  val `audio/+`       = new MimeTypes.`audio/+`("audio/+")            
  val `image/+`       = new MimeTypes.`image/+`("image/+")            
  val `message/+`     = new MimeTypes.`message/+`("message/+")    
  val `multipart/+`   = new MimeTypes.`multipart/+`("multipart/+")  
  val `text/+`        = new MimeTypes.`text/+`("text/+")       
  val `video/+`       = new MimeTypes.`video/+`("video/+")      
  
  val `application/atom+xml`              = new MimeTypes.`application/atom+xml`             
  val `application/javascript`            = new MimeTypes.`application/javascript`           
  val `application/json`                  = new MimeTypes.`application/json`                 
  val `application/octet-stream`          = new MimeTypes.`application/octet-stream`         
  val `application/ogg`                   = new MimeTypes.`application/ogg`                  
  val `application/pdf`                   = new MimeTypes.`application/pdf`                  
  val `application/postscript`            = new MimeTypes.`application/postscript`           
  val `application/soap+xml`              = new MimeTypes.`application/soap+xml`             
  val `application/xhtml+xml`             = new MimeTypes.`application/xhtml+xml`            
  val `application/xml-dtd`               = new MimeTypes.`application/xml-dtd`              
  val `application/x-javascript`          = new MimeTypes.`application/x-javascript`         
  val `application/x-shockwave-flash`     = new MimeTypes.`application/x-shockwave-flash`    
  val `application/x-www-form-urlencoded` = new MimeTypes.`application/x-www-form-urlencoded`
  val `application/zip`                   = new MimeTypes.`application/zip`                  
  
  val `audio/basic`   = new MimeTypes.`audio/basic`  
  val `audio/mp4`     = new MimeTypes.`audio/mp4`    
  val `audio/mpeg`    = new MimeTypes.`audio/mpeg`   
  val `audio/ogg`     = new MimeTypes.`audio/ogg`    
  val `audio/vorbis`  = new MimeTypes.`audio/vorbis` 
                                                     
  val `image/gif`     = new MimeTypes.`image/gif`    
  val `image/png`     = new MimeTypes.`image/png`    
  val `image/jpeg`    = new MimeTypes.`image/jpeg`   
  val `image/svg+xml` = new MimeTypes.`image/svg+xml`
  val `image/tiff`    = new MimeTypes.`image/tiff`   
  
  val `message/http`            = new MimeTypes.`message/http`           
  val `message/delivery-status` = new MimeTypes.`message/delivery-status`
  
  val `multipart/mixed`       = new MimeTypes.`multipart/mixed`      
  val `multipart/alternative` = new MimeTypes.`multipart/alternative`
  val `multipart/related`     = new MimeTypes.`multipart/related`    
  val `multipart/form-data`   = new MimeTypes.`multipart/form-data`  
  val `multipart/signed`      = new MimeTypes.`multipart/signed`     
  val `multipart/encrypted`   = new MimeTypes.`multipart/encrypted`  
  
  val `text/css`        = new MimeTypes.`text/css`       
  val `text/csv`        = new MimeTypes.`text/csv`       
  val `text/html`       = new MimeTypes.`text/html`      
  val `text/javascript` = new MimeTypes.`text/javascript`
  val `text/plain`      = new MimeTypes.`text/plain`     
  val `text/xml`        = new MimeTypes.`text/xml`       
  
  val `video/mpeg`      = new MimeTypes.`video/mpeg`     
  val `video/mp4`       = new MimeTypes.`video/mp4`      
  val `video/ogg`       = new MimeTypes.`video/ogg`      
  val `video/quicktime` = new MimeTypes.`video/quicktime`
}
