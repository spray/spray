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
import cc.spray.http.HttpResponse
import java.io.ByteArrayOutputStream
import annotation.tailrec
import java.util.zip.{Inflater, CRC32, ZipException, Deflater}

abstract class Gzip extends Decoder with Encoder {
  val encoding = HttpEncodings.gzip
  def newEncodingContext = new EncodingContext(new GzipCompressor)
  def newDecodingContext = new DecodingContext(new GzipDecompressor)
}

/**
 * An encoder and decoder for the HTTP 'gzip' encoding.
 */
object Gzip extends Gzip { self =>

  def handle(message: HttpMessage[_]) =
    message.isInstanceOf[HttpRequest] || message.asInstanceOf[HttpResponse].status.isSuccess

  def apply(minContentSize: Int) = new Gzip {
    def handle(message: HttpMessage[_]) =
      self.handle(message) && message.content.isDefined && message.content.get.buffer.length >= minContentSize
  }

  def apply(predicate: HttpMessage[_] => Boolean) = new Gzip {
    def handle(message: HttpMessage[_]) = predicate(message)
  }
}

object GzipCompressor {
  // RFC 1952: http://tools.ietf.org/html/rfc1952 section 2.2
  val Header = Array[Byte](
    31,   // ID1
    -117, // ID2
    8,    // CM = Deflate
    0,    // FLG
    0,    // MTIME 1
    0,    // MTIME 2
    0,    // MTIME 3
    0,    // MTIME 4
    0,    // XFL
    0     // OS
  )
}

class GzipCompressor extends DeflateCompressor {
  override lazy val deflater = new Deflater(Deflater.BEST_COMPRESSION, true)
  val checkSum = new CRC32 // CRC32 of uncompressed data
  var headerSent = false

  override def compress(buffer: Array[Byte], output: ByteArrayOutputStream) = {
    if (!headerSent) {
      output.write(GzipCompressor.Header)
      headerSent = true
    }
    checkSum.update(buffer)
    super.compress(buffer, output)
  }

  override def finish(output: ByteArrayOutputStream) = {
    def byte(i: Int) = (i & 0xFF).asInstanceOf[Byte]
    val crc = checkSum.getValue.asInstanceOf[Int]
    val tot = deflater.getTotalIn
    val out = super.finish(output)
    out.write(byte(crc)); out.write(byte(crc >> 8)); out.write(byte(crc >> 16)); out.write(byte(crc >> 24));
    out.write(byte(tot)); out.write(byte(tot >> 8)); out.write(byte(tot >> 16)); out.write(byte(tot >> 24));
    out
  }
}

class GzipDecompressor extends DeflateDecompressor {
  override lazy val inflater = new Inflater(true)
  val checkSum = new CRC32 // CRC32 of uncompressed data
  var headerRead = false

  override def decompress(buffer: Array[Byte], offset: Int, output: ResettableByteArrayOutputStream) = {
    decomp(buffer, offset, output, throw _)
  }

  @tailrec
  private def decomp(buffer: Array[Byte], offset: Int, output: ResettableByteArrayOutputStream,
                     produceResult: Exception => Int): Int = {
    var off = offset
    def fail(msg: String) = throw new ZipException(msg)
    def readByte(): Int = {
      if (off < buffer.length) {
        val x = buffer(off)
        off += 1
        x.toInt & 0xFF
      } else fail("Unexpected end of data")
    }
    def readShort(): Int = readByte() | (readByte() << 8)
    def readInt(): Int = readShort() | (readShort() << 16)
    def readHeader() {
      def crc16(buffer: Array[Byte], offset: Int, len: Int) = {
        val crc = new CRC32
        crc.update(buffer, offset, len)
        crc.getValue.asInstanceOf[Int] & 0xFFFF
      }
      if (readByte() != 0x1F || readByte() != 0x8B) fail("Not in GZIP format")  // check magic header
      if (readByte() != 8) fail("Unsupported GZIP compression method")        // check compression method
      val flags = readByte()
      off += 6                                         // skip MTIME, XFL and OS fields
      if ((flags & 4) > 0) off += readShort()          // skip optional extra fields
      if ((flags & 8) > 0) while (readByte() != 0) {}  // skip optional file name
      if ((flags & 16) > 0) while (readByte() != 0) {} // skip optional file comment
      if ((flags & 2) > 0 && crc16(buffer, offset, off - offset) != readShort()) fail("Corrupt GZIP header")
    }
    def readTrailer() {
      if (readInt() != checkSum.getValue.asInstanceOf[Int]) fail("Corrupt data (CRC32 checksum error)")
      if (readInt() != inflater.getBytesWritten) fail("Corrupt GZIP trailer ISIZE")
    }

    var recurse = false
    try {
      if (!headerRead) {
        readHeader()
        headerRead = true
      }
      val dataStart = output.pos
      off = super.decompress(buffer, off, output)
      checkSum.update(output.buffer, dataStart, output.pos - dataStart)
      if (inflater.finished()) {
        readTrailer()
        recurse = true
        inflater.reset()
        checkSum.reset()
        headerRead = false
      }
    }
    catch {
      case e: Exception => produceResult(e)
    }

    if (recurse && off < buffer.length) {
      val mark = output.pos
      decomp(buffer, off, output, _ => { output.resetTo(mark); off })
    } else off
  }

}