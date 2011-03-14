package cc.spray
package marshalling

import http._

abstract class AbstractMarshaller[A](implicit ma: Manifest[A]) extends Marshaller {
  val erasure = ma.erasure

  def canMarshal(obj: Any) = if (erasure.isInstance(obj)) contentTypes else Nil

  def marshal(obj: Any, contentType: ContentType) = doMarshal(obj.asInstanceOf[A], contentType)

  def contentTypes: List[ContentType]
  
  protected def doMarshal(obj: A, contentType: ContentType): RawContent
}  