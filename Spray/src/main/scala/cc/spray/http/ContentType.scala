package cc.spray.http

import Charsets._

case class ContentType(mimeType: MimeType, charset: Charset = `*`) {
  def value: String = {
    if (charset != `*`) mimeType.toString + "; charset=" + charset
    else mimeType.toString
  }
  
  def equalsOrIncludes(other: ContentType) = {
    this == other || mimeType.equalsOrIncludes(other.mimeType) && charset.equalsOrIncludes(other.charset) 
  }
}

object ContentType {
  def apply(mimeType: MimeType): ContentType = {
    apply(mimeType, if (mimeType.mainType == "text") `ISO-8859-1` else `*`)
  }
  
  implicit def fromMimeType(mimeType: MimeType): ContentType = ContentType(mimeType)
}