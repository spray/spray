package cc.spray.http

import HttpHeaders._

trait HttpMessage {

  /**
   * The HTTP headers of the request or response.
   */
  def headers: List[HttpHeader]

  /**
   * The entity body of the request or response.
   */
  def content: Option[HttpContent]

  /**
   * Returns true if a Content-Encoding header is present. 
   */
  def isEncodingSpecified: Boolean = headers.exists(_.isInstanceOf[`Content-Encoding`])
  
  /**
   * The content encoding as specified by the Content-Encoding header. If no Content-Encoding header is present the
   * default value 'identity' is returned.
   */
  lazy val encoding = headers.collect { case `Content-Encoding`(enc) => enc } match {
    case enc :: _ => enc
    case Nil => Encodings.identity
  }
  
}