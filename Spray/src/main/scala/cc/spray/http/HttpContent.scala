package cc.spray.http

import java.util.Arrays
import java.io.ByteArrayInputStream
import MimeTypes._
import Charsets._

sealed trait HttpContent {
  def isEmpty: Boolean
}

object HttpContent {
  def apply(contentType: ContentType, buffer: Array[Byte]): HttpContent = {
    if (buffer.length == 0) EmptyContent
    else new BufferContent(contentType, buffer)
  }
  
  def apply(contentType: ContentType, string: String): HttpContent = {
    if (string.isEmpty) EmptyContent
    else new BufferContent(contentType, string.getBytes(contentType.charset.nioCharset))
  }
  
  implicit def stringToOptionByteArray(string: String): HttpContent = HttpContent(`text/plain`, string)
} 

class BufferContent private[http](val contentType: ContentType, private val buffer: Array[Byte]) extends HttpContent {
  def isEmpty = false
  def length = buffer.length
  def inputStream = new ByteArrayInputStream(buffer)

  override def toString = "BufferContent(" + contentType + ',' + new String(buffer) + ')'
  override def hashCode = contentType.## * 31 + Arrays.hashCode(buffer)
  override def equals(obj: Any) = obj match {
    case o: BufferContent => contentType == o.contentType && Arrays.equals(buffer, o.buffer)
    case _ => false
  }
}

case class ObjectContent(value: Any) extends HttpContent {
  def isEmpty = false
}

case object EmptyContent extends HttpContent {
  def isEmpty = true
}