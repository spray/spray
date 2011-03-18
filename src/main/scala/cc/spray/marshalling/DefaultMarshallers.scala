package cc.spray
package marshalling

import http._
import MediaTypes._
import xml.NodeSeq
import HttpStatusCodes._

trait DefaultMarshallers {

  implicit val DefaultMarshaller = StringMarshaller orElse NodeSeqMarshaller

  object StringMarshaller extends AbstractMarshaller[String] {
    val canMarshalTo = List(ContentType(`text/plain`)) 

    def marshal(value: String, contentType: ContentType) = HttpContent(contentType, value)
  }
  
  object NodeSeqMarshaller extends AbstractMarshaller[NodeSeq] {
    val canMarshalTo = List(ContentType(`text/xml`), ContentType(`text/html`), ContentType(`application/xhtml+xml`)) 

    def marshal(value: NodeSeq, contentType: ContentType) = StringMarshaller.marshal(value.toString, contentType)
  }
  
  implicit def pimpAnyWithMarshal(obj: Any): WithMarshalExtender = new WithMarshalExtender(obj) 
  
  class WithMarshalExtender(obj: Any) {
    def marshal(accept: ContentType => Boolean)(implicit marshaller: Marshaller): Either[HttpStatus, RawContent] = {
      if (marshaller.isDefinedAt(obj)) {
        val Marshalling(canMarshalTo, convert) = marshaller(obj)
        canMarshalTo.mapFind { contentType =>
          if (accept(contentType)) Some(contentType) else None
        } match {
          case Some(contentType) => Right(convert(contentType))
          case None => Left(HttpStatus(NotAcceptable, "Resource representation is only available with these " +
                  "content-types:\n" + canMarshalTo.map(_.value).mkString("\n")))  
        }
      } else {
        Left(HttpStatus(InternalServerError, "No marshaller for response content '" + obj + "'"))
      }
    }
  }
} 