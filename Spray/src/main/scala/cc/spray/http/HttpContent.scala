package cc.spray.http

import java.util.Arrays
import java.io.ByteArrayInputStream

trait HttpContent {
  def isEmpty: Boolean
  
  def length: Int
}

object HttpContent {
  def apply(buffer: Array[Byte]): HttpContent = if (buffer.length == 0) NoContent else new ContentBuffer(buffer)
  
  implicit def stringToOptionByteArray(string: String): HttpContent = HttpContent(string.getBytes)
}

class ContentBuffer private[http](private val buffer: Array[Byte]) extends HttpContent {

  def isEmpty = false
  
  def length = buffer.length
  
  def inputStream = new ByteArrayInputStream(buffer)

  override def hashCode = Arrays.hashCode(buffer)

  override def equals(obj: Any) = obj match {
    case o: ContentBuffer => Arrays.equals(buffer, o.buffer)
    case _ => false
  }

  override def toString = "HttpContent(" + new String(buffer) + ')'
}

case object NoContent extends HttpContent {
  def isEmpty = true
  
  def length = 0
}