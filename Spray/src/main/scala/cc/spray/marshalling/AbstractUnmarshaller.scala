package cc.spray
package marshalling

import http._

abstract class AbstractUnmarshaller[A] extends Unmarshaller[A] {

  def apply(contentType: ContentType) = {
    if (canUnmarshalFrom.exists(_.matches(contentType))) Right(unmarshal) else Left(canUnmarshalFrom)
  }
  
  def canUnmarshalFrom: List[ContentTypeRange]
  
  def unmarshal(content: BufferContent): A
} 