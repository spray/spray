/*
 * Copyright (C) 2011-2012 spray.io
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

class HttpMethod private[http] (val value: String, val isSafe: Boolean, val isIdempotent: Boolean) {
  override def toString = value

  HttpMethods.register(this, value)
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  val DELETE  = new HttpMethod("DELETE" , isSafe = false, isIdempotent = true)
  val GET     = new HttpMethod("GET"    , isSafe = true , isIdempotent = true)
  val HEAD    = new HttpMethod("HEAD"   , isSafe = true , isIdempotent = true)
  val OPTIONS = new HttpMethod("OPTIONS", isSafe = false, isIdempotent = true)
  val PATCH   = new HttpMethod("PATCH"  , isSafe = false, isIdempotent = false)
  val POST    = new HttpMethod("POST"   , isSafe = false, isIdempotent = false)
  val PUT     = new HttpMethod("PUT"    , isSafe = false, isIdempotent = true)
  val TRACE   = new HttpMethod("TRACE"  , isSafe = false, isIdempotent = true)
}
