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

/**
 * @param isSafe true if the resource should not be altered on the server
 * @param isIdempotent true if requests can be safely (& automatically) repeated
 * @param entityAccepted true if meaning of request entities is properly defined
 */
case class HttpMethod private[http] (value: String,
                                     isSafe: Boolean,
                                     isIdempotent: Boolean,
                                     entityAccepted: Boolean) extends LazyValueBytesRenderable

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  private def register(m: HttpMethod): HttpMethod = register(m.value, m)

  // format: OFF
  val DELETE  = register(HttpMethod("DELETE" , isSafe = false, isIdempotent = true , entityAccepted = false))
  val GET     = register(HttpMethod("GET"    , isSafe = true , isIdempotent = true , entityAccepted = false))
  val HEAD    = register(HttpMethod("HEAD"   , isSafe = true , isIdempotent = true , entityAccepted = false))
  val OPTIONS = register(HttpMethod("OPTIONS", isSafe = true , isIdempotent = true , entityAccepted = true))
  val PATCH   = register(HttpMethod("PATCH"  , isSafe = false, isIdempotent = false, entityAccepted = true))
  val POST    = register(HttpMethod("POST"   , isSafe = false, isIdempotent = false, entityAccepted = true))
  val PUT     = register(HttpMethod("PUT"    , isSafe = false, isIdempotent = true , entityAccepted = true))
  val TRACE   = register(HttpMethod("TRACE"  , isSafe = true , isIdempotent = true , entityAccepted = false))
  // format: ON
}
