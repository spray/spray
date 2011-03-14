package cc.spray
package marshalling

import http._
import MimeTypes._

trait DefaultMarshallers {
  
  implicit val defaultMarshallers = List(StringMarshaller)
  
  object StringMarshaller extends AbstractMarshaller[String] {
    val contentTypes = List(ContentType(`text/plain`))

    protected def doMarshal(obj: String, contentType: ContentType) = HttpContent(contentType, obj)
  } 
} 