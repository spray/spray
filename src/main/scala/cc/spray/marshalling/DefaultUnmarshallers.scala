package cc.spray
package marshalling

import http._
import MediaTypes._
import MediaRanges._
import xml.{XML, NodeSeq}

trait DefaultUnmarshallers {
  
  implicit object StringUnmarshaller extends UnmarshallerBase[String] {
    val canUnmarshalFrom = List(ContentTypeRange(`text/*`))

    def unmarshal(content: HttpContent): String = {
      new String(content.buffer, content.contentType.charset.map(_.nioCharset).getOrElse {
        throw new IllegalStateException // text content should always have a Charset set
      }) 
    }
  }
  
  implicit object NodeSeqUnmarshaller extends UnmarshallerBase[NodeSeq] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: HttpContent): NodeSeq = XML.load(content.inputStream)
  }
  
  implicit def pimpHttpContentWithAs(c: Option[HttpContent]): HttpContentExtractor = new HttpContentExtractor(c) 
  
  class HttpContentExtractor(content: Option[HttpContent]) {
    def as[A](implicit unmarshaller: Unmarshaller[A]): Either[Rejection, A] = content match {
      case Some(httpContent) => unmarshaller(httpContent.contentType) match {
        case UnmarshalWith(converter) => Right(converter(httpContent))
        case CantUnmarshal(onlyFrom) => Left(UnsupportedRequestContentTypeRejection(onlyFrom))
      }
      case None => Left(RequestEntityExpectedRejection)
    }
  }
  
}

object DefaultUnmarshallers extends DefaultUnmarshallers