package cc.spray
package marshalling

import http._

trait Marshaller {
  
  def canMarshal(obj: Any): List[ContentType]
  
  def marshal(obj: Any, contentType: ContentType): RawContent
  
} 