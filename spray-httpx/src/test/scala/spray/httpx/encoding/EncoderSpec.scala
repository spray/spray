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

class EncoderSpec extends Specification with CodecSpecSupport {

  "An Encoder" should {
    "not transform the message if messageFilter returns false" in {
      val request = HttpRequest(entity = HttpEntity(smallText.getBytes("UTF8")))
      DummyEncoder.encode(request) === request
    }
    "correctly transform the HttpMessage if messageFilter returns true" in {
      val request = HttpRequest(entity = HttpEntity(smallText))
      val encoded = DummyEncoder.encode(request)
      encoded.headers === List(`Content-Encoding`(DummyEncoder.encoding))
      encoded.entity === HttpEntity(dummyCompress(smallText))
    }
  }

  def dummyCompress(s: String): String = new String(dummyCompress(s.getBytes("UTF8")), "UTF8")
  def dummyCompress(bytes: Array[Byte]): Array[Byte] = DummyCompressor.compress(bytes).finish()

  case object DummyEncoder extends Encoder {
    val messageFilter = Encoder.DefaultFilter
    val encoding = HttpEncodings.compress
    def newCompressor = DummyCompressor
  }

  case object DummyCompressor extends Compressor {
    def compress(buffer: Array[Byte]) = {
      if (buffer.length > 0) output.write(buffer, 0, buffer.length)
      output.write("compressed".getBytes("UTF8"))
      this
    }
    def flush() = getBytes
    def finish() = getBytes
  }
}
