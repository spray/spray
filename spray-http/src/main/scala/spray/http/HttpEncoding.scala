/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

sealed abstract class HttpEncodingRange extends Renderable {
  def matches(encoding: HttpEncoding): Boolean
}

case class HttpEncoding private[http] (value: String) extends HttpEncodingRange with LazyValueBytesRenderable {
  def matches(encoding: HttpEncoding) = this == encoding
}

object HttpEncoding {
  def custom(value: String): HttpEncoding = apply(value)
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {

  def register(encoding: HttpEncoding): HttpEncoding =
    register(encoding.value.toLowerCase, encoding)

  case object `*` extends HttpEncodingRange with SingletonValueRenderable {
    def matches(encoding: HttpEncoding) = true
  }

  private def register(value: String): HttpEncoding = register(HttpEncoding(value))

  // format: OFF
  val compress      = register("compress")
  val chunked       = register("chunked")
  val deflate       = register("deflate")
  val gzip          = register("gzip")
  val identity      = register("identity")
  val `x-compress`  = register("x-compress")
  val `x-zip`       = register("x-zip")
  // format: ON
}
