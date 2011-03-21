package cc.spray
package marshalling

import http._
import MediaTypes._
import xml.NodeSeq

trait DefaultMarshallers {

  implicit object StringMarshaller extends AbstractMarshaller[String] {
    val canMarshalTo = List(ContentType(`text/plain`)) 

    def marshal(value: String, contentType: ContentType) = HttpContent(contentType, value)
  }
  
  implicit object NodeSeqMarshaller extends AbstractMarshaller[NodeSeq] {
    val canMarshalTo = List(ContentType(`text/xml`), ContentType(`text/html`), ContentType(`application/xhtml+xml`)) 

    def marshal(value: NodeSeq, contentType: ContentType) = StringMarshaller.marshal(value.toString, contentType)
  }
  
}

object DefaultMarshallers extends DefaultMarshallers