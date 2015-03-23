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

sealed abstract class HttpEncodingRange extends ValueRenderable with WithQValue[HttpEncodingRange] {
  def qValue: Float
  def matches(encoding: HttpEncoding): Boolean
}

object HttpEncodingRange {
  case class `*`(qValue: Float) extends HttpEncodingRange {
    def render[R <: Rendering](r: R): r.type = if (qValue < 1.0f) r ~~ "*;q=" ~~ qValue else r ~~ '*'
    def matches(encoding: HttpEncoding) = true
    def withQValue(qValue: Float) =
      if (qValue == 1.0f) `*` else if (qValue != this.qValue) `*`(qValue.toFloat) else this
  }
  case object `*` extends `*`(1.0f)

  case class One(encoding: HttpEncoding, qValue: Float) extends HttpEncodingRange {
    def matches(encoding: HttpEncoding) = this.encoding.value.equalsIgnoreCase(encoding.value)
    def withQValue(qValue: Float) = One(encoding, qValue)
    def render[R <: Rendering](r: R): r.type = if (qValue < 1.0f) r ~~ encoding ~~ ";q=" ~~ qValue else r ~~ encoding
  }

  implicit def apply(encoding: HttpEncoding): HttpEncodingRange = apply(encoding, 1.0f)
  def apply(encoding: HttpEncoding, qValue: Float): HttpEncodingRange = One(encoding, qValue)
}

case class HttpEncoding private[http] (value: String) extends LazyValueBytesRenderable with WithQValue[HttpEncodingRange] {
  def withQValue(qValue: Float): HttpEncodingRange = HttpEncodingRange(this, qValue.toFloat)
}

object HttpEncoding {
  def custom(value: String): HttpEncoding = apply(value)
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {

  def register(encoding: HttpEncoding): HttpEncoding =
    register(encoding.value.toLowerCase, encoding)

  @deprecated("Use HttpEncodingRange.`*` instead", "1.x-RC3")
  val `*`: HttpEncodingRange = HttpEncodingRange.`*`

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
