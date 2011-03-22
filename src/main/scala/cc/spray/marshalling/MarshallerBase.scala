package cc.spray
package marshalling

import http._

trait MarshallerBase[A] extends Marshaller[A] {

  def apply(accept: ContentType => Boolean) = {
    canMarshalTo.find(accept) match {
      case Some(contentType) => MarshalWith(marshal(_, contentType))
      case None => CantMarshal(canMarshalTo)
    }
  }

  def canMarshalTo: List[ContentType]

  def marshal(value: A, contentType: ContentType): HttpContent
  
} 