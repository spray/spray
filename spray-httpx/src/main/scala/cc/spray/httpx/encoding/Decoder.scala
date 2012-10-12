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

package spray.httpx.encoding

import spray.http._


trait Decoder {
  def encoding: HttpEncoding

  def decode[T <: HttpMessage](message: T): T#Self = message.mapEntity {
    _.map((contentType, buffer) => (contentType, newDecompressor.decompress(buffer)))
  }
  
  def newDecompressor: Decompressor
}

abstract class Decompressor {
  protected val output = new ResettableByteArrayOutputStream(1024)

  def decompress(buffer: Array[Byte]): Array[Byte] = {
    output.reset()
    decompress(buffer, 0)
    output.toByteArray
  }

  protected def decompress(buffer: Array[Byte], offset: Int): Int
}