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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{ZipException, GZIPInputStream, GZIPOutputStream}
import org.parboiled.common.FileUtils
import org.specs2.mutable.Specification
import spray.util._


class GzipSpec extends Specification with CodecSpecSupport {

  "The Gzip codec" should {
    "properly encode a small string" in {
      streamGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly decode a small string" in {
      ourGunzip(streamGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly roundtip encode/decode a small string" in {
      ourGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly encode a large string" in {
      streamGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly decode a large string" in {
      ourGunzip(streamGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode a large string" in {
      ourGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "provide a better compression ratio than the standard Gzipr/Gunzip streams" in {
      ourGzip(largeTextBytes).length must be_< (streamGzip(largeTextBytes).length)
    }
    "properly decode concatenated compressions" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), gzip("User!")).flatten) must readAs("Hello, dear User!")
    }
    "throw an error on corrupt input" in {
      ourGunzip(corruptGzipContent) must throwA[ZipException]("(invalid literal/length code|Corrupt data \\(CRC32 checksum error\\))")
    }
    "not throw an error if a subsequent block is corrupt" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), corruptGzipContent).flatten) must readAs("Hello, dear ")
    }
    "support chunked round-trip encoding/decoding" in {
      val chunks = largeTextBytes.grouped(512).toArray
      val comp = Gzip.newCompressor
      val decomp = Gzip.newDecompressor
      val chunks2 =
        chunks.map { chunk => decomp.decompress(comp.compress(chunk).flush()) } :+ decomp.decompress(comp.finish())
      chunks2.flatten must readAs(largeText)
    }
  }

  def gzip(s: String) = ourGzip(s.getBytes("UTF8"))
  def ourGzip(bytes: Array[Byte]) = Gzip.newCompressor.compress(bytes).finish()
  def ourGunzip(bytes: Array[Byte]) = Gzip.newDecompressor.decompress(bytes)

  lazy val corruptGzipContent = make(gzip("Hello")) { _.update(14, 26.toByte) }

  def streamGzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val gos = new GZIPOutputStream(output); gos.write(bytes); gos.close()
    output.toByteArray
  }

  def streamGunzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    FileUtils.copyAll(new GZIPInputStream(new ByteArrayInputStream(bytes)), output)
    output.toByteArray
  }

}