package cc.spray
package marshalling

import http._

abstract class AbstractUnmarshaller[A] extends Unmarshaller[A] {

  def apply(contentType: ContentType) = {
    if (canUnmarshalFrom.exists(_.matches(contentType))) {
      UnmarshalWith(unmarshal)
    } else {
      CantUnmarshal(canUnmarshalFrom)
    }
  }
  
  def canUnmarshalFrom: List[ContentTypeRange]
  
  def unmarshal(content: HttpContent): A
} 