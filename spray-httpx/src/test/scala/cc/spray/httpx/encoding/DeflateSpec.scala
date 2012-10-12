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

import java.io.ByteArrayOutputStream
import java.util.zip.{InflaterOutputStream, DeflaterOutputStream}
import org.specs2.mutable.Specification


class DeflateSpec extends Specification with CodecSpecSupport {

  "The Deflate codec" should {
    "properly encode a small string" in {
      streamInflate(ourDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly decode a small string" in {
      ourInflate(streamDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly roundtip encode/decode a small string" in {
      ourInflate(ourDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly encode a large string" in {
      streamInflate(ourDeflate(largeTextBytes)) must readAs(largeText)
    }
    "properly decode a large string" in {
      ourInflate(streamDeflate(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode a large string" in {
      ourInflate(ourDeflate(largeTextBytes)) must readAs(largeText)
    }
    "provide a better compression ratio than the standard Deflater/Inflater streams" in {
      ourDeflate(largeTextBytes).length must be_< (streamDeflate(largeTextBytes).length)
    }
    "support chunked round-trip encoding/decoding" in {
      val chunks = largeTextBytes.grouped(512).toArray
      val comp = Deflate.newCompressor
      val decomp = Deflate.newDecompressor
      val chunks2 =
        chunks.map { chunk => decomp.decompress(comp.compress(chunk).flush()) } :+ decomp.decompress(comp.finish())
      chunks2.flatten must readAs(largeText)
    }
  }

  def ourDeflate(bytes: Array[Byte]) = Deflate.newCompressor.compress(bytes).finish()
  def ourInflate(bytes: Array[Byte]) = Deflate.newDecompressor.decompress(bytes)

  def streamDeflate(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val dos = new DeflaterOutputStream(output); dos.write(bytes); dos.close()
    output.toByteArray
  }

  def streamInflate(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val ios = new InflaterOutputStream(output); ios.write(bytes); ios.close()
    output.toByteArray
  }
}