package cc.spray
package marshalling

import http._
import MimeTypes._

trait DefaultUnmarshallers {
  
  implicit val defaultUnmarshallers = List(StringUnmarshaller)
  
  object StringUnmarshaller extends Unmarshaller[String] {
    val canUnmarshalFrom = List(ContentType(`text/+`))

    def unmarshal(content: BufferContent): String = {
      new String(content.buffer, content.contentType.charset.nioCharset)
    }
  } 
} 