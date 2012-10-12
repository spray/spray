/*
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package spray.http

sealed abstract class HttpEncodingRange {
  def value: String
  def matches(encoding: HttpEncoding): Boolean
  override def toString = "HttpEncodingRange(" + value + ')'
}

sealed abstract class HttpEncoding extends HttpEncodingRange {
  def matches(encoding: HttpEncoding) = this == encoding
  override def equals(obj: Any) = obj match {
    case x: HttpEncoding => (this eq x) || value == x.value
    case _ => false
  }
  override def hashCode() = value.##
  override def toString = "HttpEncoding(" + value + ')'
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {
  
  def register(encoding: HttpEncoding): HttpEncoding = {
    register(encoding, encoding.value.toLowerCase)
    encoding
  }
  
  val `*`: HttpEncodingRange = new HttpEncodingRange {
    def value = "*"
    def matches(encoding: HttpEncoding) = true
  }
  
  private class PredefEncoding(val value: String) extends HttpEncoding
  
  val compress      = register(new PredefEncoding("compress"))
  val chunked       = register(new PredefEncoding("chunked"))
  val deflate       = register(new PredefEncoding("deflate"))
  val gzip          = register(new PredefEncoding("gzip"))
  val identity      = register(new PredefEncoding("identity"))
  val `x-compress`  = register(new PredefEncoding("x-compress"))
  val `x-zip`       = register(new PredefEncoding("x-zip"))

  case class CustomHttpEncoding(value: String) extends HttpEncoding
}
