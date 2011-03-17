package cc.spray
package marshalling

import http._

abstract class AbstractMarshaller[A] extends Marshaller {
  private val erasure = getClass.
          getTypeArgumentsOf(classOf[AbstractMarshaller[_]]).head.
          getOrElse(throw new RuntimeException("Can not resolve type argument to AbstractMarshaller"))
  
  def isDefinedAt(x: Any) = erasure.isInstance(x)

  def apply(x: Any) = Marshalling(canMarshalTo, marshal(x.asInstanceOf[A], _))

  def canMarshalTo: List[ContentType]

  def marshal(value: A, contentType: ContentType): RawContent
  
} 