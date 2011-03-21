package cc.spray.http

import java.util.Arrays
import java.io.ByteArrayInputStream
import Charsets._
import MediaTypes._

object HttpContent {
  def apply(string: String): HttpContent = apply(ContentType(`text/plain`), string)
  
  def apply(contentType: ContentType, string: String): HttpContent = {
    apply(contentType, string.getBytes(contentType.charset.getOrElse(`ISO-8859-1`).nioCharset))
  }
  
  def apply(contentType: ContentType, buffer: Array[Byte]): HttpContent = new HttpContent(contentType, buffer)
} 

class HttpContent private[http](val contentType: ContentType, private[spray] val buffer: Array[Byte]) {
  def isEmpty = false
  def length = buffer.length
  def inputStream = new ByteArrayInputStream(buffer)

  override def toString = "HttpContent(" + contentType + ',' + new String(buffer, contentType.charset.getOrElse(`ISO-8859-1`).nioCharset) + ')'
  override def hashCode = contentType.## * 31 + Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case o: HttpContent => contentType == o.contentType && Arrays.equals(buffer, o.buffer)
    case _ => false
  }
}
