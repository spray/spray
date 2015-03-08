/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
 * @param isEntityAccepted true if meaning of request entities is properly defined
 */
case class HttpMethod private[http] (value: String,
                                     isSafe: Boolean,
                                     isIdempotent: Boolean,
                                     isEntityAccepted: Boolean) extends LazyValueBytesRenderable {
  // for faster equality checks we use the hashcode of the method name (and make sure it's distinct during registration)
  private[http] val fingerprint = value.##

  def name = value

  override def hashCode(): Int = fingerprint
  override def equals(obj: Any): Boolean =
    obj match {
      case m: HttpMethod ⇒ fingerprint == m.fingerprint
      case _             ⇒ false
    }
}

object HttpMethod {
  def custom(value: String, safe: Boolean, idempotent: Boolean, entityAccepted: Boolean): HttpMethod = {
    require(value.nonEmpty, "value must be non-empty")
    require(!safe || idempotent, "An HTTP method cannot be safe without being idempotent")
    apply(value, safe, idempotent, entityAccepted)
  }
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  def register(method: HttpMethod): HttpMethod = {
    registry.values foreach { m ⇒ if (m.fingerprint == method.fingerprint) sys.error("Method fingerprint collision") }
    register(method.value, method)
  }

  // format: OFF
  val CONNECT = register(HttpMethod("CONNECT", isSafe = false, isIdempotent = false, isEntityAccepted = false))
  val DELETE  = register(HttpMethod("DELETE" , isSafe = false, isIdempotent = true , isEntityAccepted = false))
  val GET     = register(HttpMethod("GET"    , isSafe = true , isIdempotent = true , isEntityAccepted = false))
  val HEAD    = register(HttpMethod("HEAD"   , isSafe = true , isIdempotent = true , isEntityAccepted = false))
  val OPTIONS = register(HttpMethod("OPTIONS", isSafe = true , isIdempotent = true , isEntityAccepted = true))
  val PATCH   = register(HttpMethod("PATCH"  , isSafe = false, isIdempotent = false, isEntityAccepted = true))
  val POST    = register(HttpMethod("POST"   , isSafe = false, isIdempotent = false, isEntityAccepted = true))
  val PUT     = register(HttpMethod("PUT"    , isSafe = false, isIdempotent = true , isEntityAccepted = true))
  val TRACE   = register(HttpMethod("TRACE"  , isSafe = true , isIdempotent = true , isEntityAccepted = false))
  // format: ON
}
