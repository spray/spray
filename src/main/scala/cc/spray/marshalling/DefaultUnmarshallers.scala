package cc.spray
package marshalling

import http._
import MediaTypes._
import MediaRanges._
import xml.{XML, NodeSeq}
import HttpStatusCodes._

trait DefaultUnmarshallers {
  
  implicit object StringUnmarshaller extends AbstractUnmarshaller[String] {
    val canUnmarshalFrom = List(ContentTypeRange(`text/*`))

    def unmarshal(content: BufferContent): String = {
      new String(content.buffer, content.contentType.charset.map(_.nioCharset).getOrElse {
        throw new IllegalStateException // text content should always have a Charset set
      }) 
    }
  }
  
  implicit object NodeSeqUnmarshaller extends AbstractUnmarshaller[NodeSeq] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: BufferContent): NodeSeq = XML.load(content.inputStream)
  }
  
  implicit def pimpHttpContentWithAs(c: HttpContent): HttpContentExtractor = new HttpContentExtractor(c) 
  
  class HttpContentExtractor(content: HttpContent) {
    def as[A](implicit ma: Manifest[A], unmarshaller: Unmarshaller[A]): Either[HttpStatus, A] = content match {
      case x: BufferContent => unmarshaller(x.contentType) match {
        case UnmarshalWith(converter) => Right(converter(x))
        case CantUnmarshal(onlyFrom) => Left(HttpStatus(UnsupportedMediaType,
          "The requests content-type must be one the following:\n" + onlyFrom.map(_.value).mkString("\n"))) 
      }
      case ObjectContent(x) => {
        if (ma.erasure.isInstance(x)) Right(x.asInstanceOf[A])
        else Left(HttpStatus(InternalServerError, "Cannot unmarshal ObjectContent"))
      }
      case EmptyContent => {
        if (ma.erasure == classOf[Unit]) Right(().asInstanceOf[A])
        else Left(HttpStatus(BadRequest, "Request entity expected"))
      }
    }
  }
  
}

object DefaultUnmarshallers extends DefaultUnmarshallers