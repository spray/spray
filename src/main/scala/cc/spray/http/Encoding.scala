package cc.spray.http

import cc.spray.utils.ObjectRegistry

sealed trait Encoding {
  def value: String   
  override def toString = value
  
  Encodings.register(this, value)
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object Encodings extends ObjectRegistry[String, Encoding] {
  
  class StandardEncoding private[Encodings] (val value: String) extends Encoding
  
  case class CustomEncoding(value: String) extends Encoding
  
  val `*`           = new StandardEncoding("*")
  val compress      = new StandardEncoding("compress")
  val chunked       = new StandardEncoding("chunked") 
  val deflate       = new StandardEncoding("deflate") 
  val gzip          = new StandardEncoding("gzip") 
  val identity      = new StandardEncoding("identity")
  val `x-compress`  = new StandardEncoding("x-compress")
  val `x-zip`       = new StandardEncoding("x-zip")
}