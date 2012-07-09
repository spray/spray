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

package cc.spray.httpx.encoding

import java.io.ByteArrayOutputStream
import cc.spray.http._
import HttpHeaders._


trait Encoder {
  def encoding: HttpEncoding

  def handles(message: HttpMessage) = message match {
    case _: HttpRequest => true
    case x: HttpResponse => x.status.isSuccess
  }

  def encode[T <: HttpMessage](message: T): T#Self = message.entity match {
    case HttpBody(contentType, buffer) if handles(message) && !message.isEncodingSpecified =>
      message.withHeadersAndEntity(
        headers = `Content-Encoding`(encoding) :: message.headers,
        entity = HttpBody(contentType, newCompressor.compress(buffer).finish())
      )

    case _ => message.message
  }

  def startEncoding[T <: HttpMessage](message: HttpMessage#Self): Option[(HttpMessage#Self, Compressor)] = {
    if (handles(message) && !message.isEncodingSpecified) {
      message.entity.toOption.map { case HttpBody(contentType, buffer) =>
        val compressor = newCompressor
        message.withHeadersAndEntity(
          headers = `Content-Encoding`(encoding) :: message.headers,
          entity = HttpBody(contentType, compressor.compress(buffer).flush())
        ) -> compressor
      }
    } else None
  }

  def newCompressor: Compressor
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