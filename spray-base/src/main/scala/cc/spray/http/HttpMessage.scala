package cc.spray.http

import HttpHeaders._

abstract class HttpMessage[T <: HttpMessage[T]] {

  /**
   * The HTTP headers of the request or response.
   */
  def headers: List[HttpHeader]

  /**
   * The entity body of the request or response.
   */
  def content: Option[HttpContent]

  /**
   * Creates a copy of this message replacing the headers with the given ones.
   */
  def withHeaders(headers: List[HttpHeader]): T

  /**
   * Creates a copy of this message replacing the content with the given one.
   */
  def withContent(content: Option[HttpContent]): T

  /**
   * Creates a copy of this message replacing the headers and content with the given ones.
   */
  def withHeadersAndContent(headers: List[HttpHeader], content: Option[HttpContent]): T

  /**
   * Creates a copy of this message with the content transformed by the given function.
   */
  def withContentTransformed(f: HttpContent => HttpContent) = withContent(content.map(f))

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
    case Nil => HttpEncodings.identity
  }
  
}