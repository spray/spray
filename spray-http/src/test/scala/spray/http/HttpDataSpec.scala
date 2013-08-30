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

package spray.http

import java.io.File
import org.parboiled.common.FileUtils
import org.specs2.mutable.Specification

class HttpDataSpec extends Specification {

  "HttpData" should {
    "properly support `copyToArray`" in {
      "HttpData.Bytes" in {
        test(HttpData("Ken sent me!"))
      }
      "HttpData.FileBytes" in {
        val file = File.createTempFile("spray-http_HttpDataSpec", ".txt")
        try {
          FileUtils.writeAllText("Ken sent me!", file)
          test(HttpData(file))

        } finally file.delete
      }
      "HttpData.Compound" in {
        test(HttpData("Ken") +: HttpData(" sent") +: HttpData(" me") +: HttpData("!"))
      }
    }
  }

  def test(data: HttpData) = {
    testCopyToArray(data, sourceOffset = 0, targetOffset = 0, span = 12) === "Ken sent me!xxxx"
    testCopyToArray(data, sourceOffset = 0, targetOffset = 2, span = 12) === "xxKen sent me!xx"
    testCopyToArray(data, sourceOffset = 0, targetOffset = 4, span = 12) === "xxxxKen sent me!"
    testCopyToArray(data, sourceOffset = 0, targetOffset = 6, span = 12) === "xxxxxxKen sent m"
    testCopyToArray(data, sourceOffset = 2, targetOffset = 0, span = 12) === "n sent me!xxxxxx"
    testCopyToArray(data, sourceOffset = 8, targetOffset = 0, span = 12) === " me!xxxxxxxxxxxx"
    testCopyToArray(data, sourceOffset = 8, targetOffset = 10, span = 2) === "xxxxxxxxxx mxxxx"
    testCopyToArray(data, sourceOffset = 8, targetOffset = 10, span = 8) === "xxxxxxxxxx me!xx"
  }

  def testCopyToArray(data: HttpData, sourceOffset: Long, targetOffset: Int, span: Int): String = {
    val array = "xxxxxxxxxxxxxxxx".getBytes
    data.copyToArray(array, sourceOffset, targetOffset, span)
    new String(array)
  }
}