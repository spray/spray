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

package spray.httpx.encoding

import spray.http._
import HttpHeaders._
import org.specs2.mutable.Specification

class DecoderSpec extends Specification with CodecSpecSupport {

  "A Decoder" should {
    "not transform the message if it doesn't contain a Content-Encoding header" in {
      val request = HttpRequest(entity = HttpEntity(smallText))
      DummyDecoder.decode(request) === request
    }
    "correctly transform the message if it contains a Content-Encoding header" in {
      val request = HttpRequest(entity = HttpEntity(smallText), headers = List(`Content-Encoding`(DummyDecoder.encoding)))
      val decoded = DummyDecoder.decode(request)
      decoded.headers === Nil
      decoded.entity === HttpEntity(dummyDecompress(smallText))
    }
  }

  def dummyDecompress(s: String): String = new String(dummyDecompress(s.getBytes("UTF8")), "UTF8")
  def dummyDecompress(bytes: Array[Byte]): Array[Byte] = DummyDecompressor.decompress(bytes)

  case object DummyDecoder extends Decoder {
    val encoding = HttpEncodings.compress
    def newDecompressor = DummyDecompressor
  }

  case object DummyDecompressor extends Decompressor {
    protected def decompress(buffer: Array[Byte], offset: Int) = {
      output.write(buffer, 0, buffer.length)
      output.write("compressed".getBytes("UTF8"))
      -1
    }
  }
}
