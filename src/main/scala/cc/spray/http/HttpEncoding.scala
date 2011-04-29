/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.http

import cc.spray.utils.ObjectRegistry

sealed trait HttpEncodingRange {
  def value: String
  def matches(encoding: HttpEncoding): Boolean
  override def toString = "HttpEncodingRange(" + value + ')'
}

sealed trait HttpEncoding extends HttpEncodingRange {
  def value: String
  def matches(encoding: HttpEncoding) = value == encoding.value
  override def toString = value
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {
  
  def register(encoding: HttpEncoding) { register(encoding, encoding.value) }
  
  val `*` = new HttpEncodingRange {
    def value = "*"
    def matches(encoding: HttpEncoding) = true
  }
  
  class StandardHttpEncoding private[HttpEncodings] (val value: String) extends HttpEncoding {
    register(this)
  }
  
  case class CustomHttpEncoding(value: String) extends HttpEncoding
  
  val compress      = new StandardHttpEncoding("compress")
  val chunked       = new StandardHttpEncoding("chunked") 
  val deflate       = new StandardHttpEncoding("deflate") 
  val gzip          = new StandardHttpEncoding("gzip") 
  val identity      = new StandardHttpEncoding("identity")
  val `x-compress`  = new StandardHttpEncoding("x-compress")
  val `x-zip`       = new StandardHttpEncoding("x-zip")
}