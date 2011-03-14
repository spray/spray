package cc.spray
package marshalling

import http._

abstract class AbstractUnmarshaller[A] extends Unmarshaller[A] {

  def apply(contentType: ContentType) = {
    if (canUnmarshalFrom.exists(_.equalsOrIncludes(contentType))) Right(unmarshal) else Left(canUnmarshalFrom)
  }
  
  def canUnmarshalFrom: List[ContentType]
  
  def unmarshal(content: BufferContent): A
} 