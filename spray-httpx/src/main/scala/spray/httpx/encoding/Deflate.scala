/*
 * Copyright (C) 2011-2013 spray.io
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

import java.util.zip.{ DataFormatException, ZipException, Inflater, Deflater }
import scala.annotation.tailrec
import spray.util._
import spray.http._

class Deflate(val messageFilter: HttpMessage ⇒ Boolean) extends Decoder with Encoder {
  val encoding = HttpEncodings.deflate
  def newCompressor = new DeflateCompressor
  def newDecompressor = new DeflateDecompressor
}

/**
 * An encoder and decoder for the HTTP 'deflate' encoding.
 */
object Deflate extends Deflate(Encoder.DefaultFilter) {
  def apply(messageFilter: MessagePredicate) = new Deflate(messageFilter)
}

class DeflateCompressor extends Compressor {
  protected lazy val deflater = new Deflater(Deflater.BEST_COMPRESSION, false)
  private val outputBuf = new Array[Byte](1024) // use a working buffer of size 1 KB)

  def compress(buffer: Array[Byte]) = {
    @tailrec
    def doCompress(offset: Int = 0): Unit = {
      deflater.setInput(buffer, offset, math.min(outputBuf.length, buffer.length - offset))
      drain()
      val nextOffset = offset + outputBuf.length
      if (nextOffset < buffer.length) doCompress(nextOffset)
    }
    if (buffer.length > 0) doCompress()
    this
  }

  def flush() = {
    // trick the deflater into flushing: switch compression level
    deflater.setInput(EmptyByteArray, 0, 0)
    deflater.setLevel(Deflater.NO_COMPRESSION)
    drain()
    deflater.setLevel(Deflater.BEST_COMPRESSION)
    drain()
    getBytes
  }

  override def finish() = {
    deflater.finish()
    drain()
    deflater.end()
    getBytes
  }

  @tailrec
  protected final def drain(): Unit = {
    val len = deflater.deflate(outputBuf)
    if (len > 0) {
      output.write(outputBuf, 0, len)
      drain()
    }
  }
}

class DeflateDecompressor extends Decompressor {
  protected lazy val inflater = new Inflater()
  private val outputBuf = new Array[Byte](1024) // use a working buffer of size 1 KB)

  protected def decompress(buffer: Array[Byte], offset: Int) = {
    @tailrec
    def doDecompress(off: Int): Unit = {
      inflater.setInput(buffer, off, math.min(1024, buffer.length - off))
      drain()
      if (inflater.needsDictionary) throw new ZipException("ZLIB dictionary missing")
      val nextOffset = off + 1024
      if (nextOffset < buffer.length && !inflater.finished()) doDecompress(nextOffset)
    }
    try {
      if (buffer.length > 0) {
        doDecompress(offset)
        buffer.length - inflater.getRemaining
      } else 0
    } catch {
      case e: DataFormatException ⇒
        throw new ZipException(e.getMessage.toOption getOrElse "Invalid ZLIB data format")
    }
  }

  @tailrec
  private def drain(): Unit = {
    val len = inflater.inflate(outputBuf)
    if (len > 0) {
      output.write(outputBuf, 0, len)
      drain()
    }
  }
}
