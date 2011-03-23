/*
 * Copyright (C) 2011 Mathias Doenitz
 * Heavily inspired by the "blueeyes" framework (http://github.com/jdegoes/blueeyes)
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