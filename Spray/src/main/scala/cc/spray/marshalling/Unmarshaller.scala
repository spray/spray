package cc.spray
package marshalling

import http._

trait Unmarshaller[A] {
  
  def canUnmarshalFrom: List[ContentType]
  
  def unmarshal(buffer: BufferContent): A
  
} 