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

class HttpMethod private[http] (val value: String) {
  override def toString = value
  
  HttpMethods.register(this, value)
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  val DELETE  = new HttpMethod("DELETE")
  val GET     = new HttpMethod("GET")
  val HEAD    = new HttpMethod("HEAD")
  val OPTIONS = new HttpMethod("OPTIONS")
  val PATCH   = new HttpMethod("PATCH")
  val POST    = new HttpMethod("POST")
  val PUT     = new HttpMethod("PUT")
  val TRACE   = new HttpMethod("TRACE")
}
