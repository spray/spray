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

import java.lang.IllegalStateException
import spray.http._


/**
 * An encoder and decoder for the HTTP 'identity' encoding.
 */
object NoEncoding extends Decoder with Encoder {
  val encoding = HttpEncodings.identity

  override def encode[T <: HttpMessage](message: T) = message.message
  override def decode[T <: HttpMessage](message: T) = message.message

  val messageFilter: HttpMessage => Boolean = _ => false

  def newCompressor = NoEncodingCompressor
  def newDecompressor = NoEncodingDecompressor
}

class NoEncodingCompressor(private var buffer: Array[Byte]) extends Compressor {
  def compress(buffer: Array[Byte]) = { this.buffer = buffer; this }
  def flush() = buffer
  def finish() = buffer
}

object NoEncodingCompressor extends NoEncodingCompressor(spray.util.EmptyByteArray)

object NoEncodingDecompressor extends Decompressor {
  override def decompress(buffer: Array[Byte]) = buffer
  protected def decompress(buffer: Array[Byte], offset: Int) = throw new IllegalStateException
}