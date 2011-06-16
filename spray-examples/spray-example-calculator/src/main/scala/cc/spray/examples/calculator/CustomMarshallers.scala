package cc.spray.examples.calculator

import cc.spray._
import http._
import marshalling._
import MediaTypes._

trait CustomMarshallers {
  
  implicit object DoubleMarshaller extends MarshallerBase[Double] {
    val canMarshalTo = ContentType(`text/xml`) :: ContentType(`text/plain`) :: Nil

    def marshal(value: Double, contentType: ContentType) = contentType match {
      case x@ ContentType(`text/xml`, _) => HttpContent(x, "<double>" + value + "</double>")  
      case x@ ContentType(`text/plain`, _) => HttpContent(x, value.toString)
      case _ => throw new IllegalArgumentException
    }
  }
  
}