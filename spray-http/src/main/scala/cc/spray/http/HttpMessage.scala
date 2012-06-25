/*
 * Copyright (C) 2011-2012 spray.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
   * Creates a copy of this message with the heders transformed by the given function.
   */
  def withHeadersTransformed(f: List[HttpHeader] => List[HttpHeader]): T = {
    val transformed = f(headers)
    if (transformed eq headers) this.asInstanceOf[T] else withHeaders(transformed)
  }

  /**
   * Creates a copy of this message with the content transformed by the given function.
   */
  def withContentTransformed(f: HttpContent => HttpContent): T = content match {
    case Some(content) =>
      val transformed = f(content)
      if (transformed eq content) this.asInstanceOf[T] else withContent(Some(transformed))
    case None => this.asInstanceOf[T]
  }

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