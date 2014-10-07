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

package spray.httpx.encoding

import java.io.ByteArrayOutputStream
import spray.http._
import HttpHeaders._

trait Encoder {
  def encoding: HttpEncoding

  def messageFilter: HttpMessage ⇒ Boolean

  def encode[T <: HttpMessage](message: T): T#Self = message.entity match {
    case HttpEntity.NonEmpty(contentType, data) if messageFilter(message) && !message.headers.exists(Encoder.isContentEncodingHeader) ⇒
      message.withHeadersAndEntity(
        headers = `Content-Encoding`(encoding) :: message.headers,
        entity = HttpEntity(contentType, newCompressor.compress(data.toByteArray).finish()))

    case _ ⇒ message.message
  }

  def startEncoding[T <: HttpMessage](message: T): Option[(T#Self, Compressor)] =
    if (messageFilter(message) && !message.headers.exists(Encoder.isContentEncodingHeader))
      Some {
        val compressor = newCompressor
        val newEntity = message.entity match {
          case HttpEntity.Empty                       ⇒ HttpEntity.Empty
          case HttpEntity.NonEmpty(contentType, data) ⇒ HttpEntity(contentType, compressor.compress(data.toByteArray).flush())
        }

        message.withHeadersAndEntity(
          headers = `Content-Encoding`(encoding) :: message.headers,
          entity = newEntity) -> compressor
      }
    else None

  def newCompressor: Compressor
}

object Encoder {
  import MessagePredicate._
  val DefaultFilter = (isRequest || responseStatus(_.isSuccess)) && isCompressible

  private[encoding] val isContentEncodingHeader: HttpHeader ⇒ Boolean = _.isInstanceOf[`Content-Encoding`]
}

abstract class Compressor {
  protected lazy val output = new ByteArrayOutputStream(1024)

  def compress(buffer: Array[Byte]): this.type

  def flush(): Array[Byte]

  def finish(): Array[Byte]

  protected def getBytes: Array[Byte] = {
    val bytes = output.toByteArray
    output.reset()
    bytes
  }
}