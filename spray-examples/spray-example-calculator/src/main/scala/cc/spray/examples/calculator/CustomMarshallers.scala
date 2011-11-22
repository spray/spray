package cc.spray
package examples.calculator

import http._
import typeconversion._
import MediaTypes._

trait CustomMarshallers {
  
  implicit object DoubleMarshaller extends SimpleMarshaller[Double] {
    val canMarshalTo = ContentType(`text/xml`) :: ContentType(`text/plain`) :: Nil

    def marshal(value: Double, contentType: ContentType) = contentType match {
      case x@ ContentType(`text/xml`, _) => HttpContent(x, "<double>" + value + "</double>")  
      case x@ ContentType(`text/plain`, _) => HttpContent(x, value.toString)
      case _ => throw new IllegalArgumentException
    }
  }
  
}