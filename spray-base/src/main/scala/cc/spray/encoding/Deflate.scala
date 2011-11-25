/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package encoding

import http._
import annotation.tailrec
import java.io.{OutputStream, ByteArrayOutputStream}
import java.util.zip.{DataFormatException, ZipException, Inflater, Deflater}

abstract class Deflate extends Decoder with Encoder {
  val encoding = HttpEncodings.deflate
  def newEncodingContext = new EncodingContext(new DeflateCompressor)
  def newDecodingContext = new DecodingContext(new DeflateDecompressor)
}

/**
 * An encoder and decoder for the HTTP 'deflate' encoding.
 */
object Deflate extends Deflate { self =>

  def handle(message: HttpMessage[_]) =
    message.isInstanceOf[HttpRequest] || message.asInstanceOf[HttpResponse].status.isSuccess

  def apply(minContentSize: Int) = new Deflate {
    def handle(message: HttpMessage[_]) =
      self.handle(message) && message.content.isDefined && message.content.get.buffer.length >= minContentSize
  }

  def apply(predicate: HttpMessage[_] => Boolean) = new Deflate {
    def handle(message: HttpMessage[_]) = predicate(message)
  }
}

class DeflateCompressor extends Compressor {
  lazy val deflater = new Deflater(Deflater.BEST_COMPRESSION, false)
  val outputBuf = new Array[Byte](1024) // use a working buffer of size 1 KB)

  def compress(buffer: Array[Byte], output: ByteArrayOutputStream) = {
    @tailrec
    def doCompress(offset: Int = 0) {
      deflater.setInput(buffer, offset, math.min(outputBuf.length, buffer.length - offset))
      drainTo(output)
      val nextOffset = offset + outputBuf.length
      if (nextOffset < buffer.length) doCompress(nextOffset)
    }
    if (buffer.length > 0) doCompress()
    output
  }

  def flush(output: ByteArrayOutputStream) = {
    // trick the deflater into flushing: switch compression level
    deflater.setInput(utils.EmptyByteArray, 0, 0)
    deflater.setLevel(Deflater.NO_COMPRESSION)
    drainTo(output)
    deflater.setLevel(Deflater.BEST_COMPRESSION)
    drainTo(output)
  }

  def finish(output: ByteArrayOutputStream) = {
    deflater.finish()
    drainTo(output)
    deflater.end()
    output
  }

  @tailrec
  protected final def drainTo(output: ByteArrayOutputStream): ByteArrayOutputStream = {
    val len = deflater.deflate(outputBuf)
    if (len > 0) {
      output.write(outputBuf, 0, len)
      drainTo(output)
    } else output
  }
}

class DeflateDecompressor extends Decompressor {
  lazy val inflater = new Inflater()
  val outputBuf = new Array[Byte](1024) // use a working buffer of size 1 KB)

  def decompress(buffer: Array[Byte], offset: Int, output: ResettableByteArrayOutputStream) = {
    @tailrec
    def doDecompress(off: Int) {
      inflater.setInput(buffer, off, math.min(512, buffer.length - off))
      drainTo(output)
      if (inflater.needsDictionary) throw new ZipException("ZLIB dictionary missing")
      val nextOffset = off + 512
      if (nextOffset < buffer.length && !inflater.finished()) doDecompress(nextOffset)
    }
    try {
      if (buffer.length > 0) {
        doDecompress(offset)
        buffer.length - inflater.getRemaining
      } else 0
    } catch {
      case e: DataFormatException =>
        throw new ZipException(if (e.getMessage != null) e.getMessage else "Invalid ZLIB data format")
    }
  }

  @tailrec
  protected final def drainTo(output: OutputStream) {
    val len = inflater.inflate(outputBuf)
    if (len > 0) {
      output.write(outputBuf, 0, len)
      drainTo(output)
    }
  }
}
