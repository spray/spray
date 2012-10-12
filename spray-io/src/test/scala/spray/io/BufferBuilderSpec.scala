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

package spray.io

import org.specs2.mutable.Specification
import spray.util._

class BufferBuilderSpec extends Specification {

  "A BufferBuilder" should {
    "initially be empty" in {
      BufferBuilder() must haveContent("")
      BufferBuilder(16) must haveContent("")
    }
    "properly collect ASCII string content within capacity" in {
      BufferBuilder(16).append("Yeah") must haveContent("Yeah")
    }
    "properly collect ASCII string content exceeding capacity" in {
      BufferBuilder(8).append("Yeah").append(" ").append("absolutely!") must haveContent("Yeah absolutely!")
    }
    "properly collect byte array content within capacity" in {
      BufferBuilder(16).append("Yeah".getBytes) must haveContent("Yeah")
    }
    "properly collect byte array content exceeding capacity" in {
      BufferBuilder(8).append("Yeah".getBytes).append(" ".getBytes).append("absolutely!".getBytes) must
        haveContent("Yeah absolutely!")
    }
    "properly produce results as per toArray" in {
      BufferBuilder(4).append("Hello").toArray === Array(72, 101, 108, 108, 111)
    }
  }

  def haveContent(s: String) = beEqualTo(s) ^^ { bb: BufferBuilder => bb.toByteBuffer.drainToString }
}
