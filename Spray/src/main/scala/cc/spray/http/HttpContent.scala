package cc.spray.http

import java.util.Arrays
import java.io.ByteArrayInputStream
import MediaTypes._
import Charsets._

sealed trait HttpContent {
  def isEmpty: Boolean
}

sealed trait RawContent extends HttpContent

object HttpContent {
  def apply(contentType: ContentType, buffer: Array[Byte]): RawContent = {
    if (buffer.length == 0) EmptyContent
    else new BufferContent(contentType, buffer)
  }
  
  def apply(contentType: ContentType, string: String): RawContent = {
    if (string.isEmpty) EmptyContent
    else {
      new BufferContent(contentType, string.getBytes(contentType.charset.getOrElse(`ISO-8859-1`).nioCharset))
    }
  }
} 

class BufferContent private[http](val contentType: ContentType, private[spray] val buffer: Array[Byte]) extends RawContent {
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

case object EmptyContent extends RawContent {
  def isEmpty = true
}

case class ObjectContent(value: Any) extends HttpContent {
  def isEmpty = false
}
