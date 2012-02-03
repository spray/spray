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
package directives

import http._
import test.AbstractSprayTest
import MediaTypes._
import HttpCharsets._

class ChunkingDirectivesSpec extends AbstractSprayTest {

  "The 'autoChunk' directive" should {
    "correctly split the inner response into chunks" in {
      val result = test(HttpRequest(uri = "/a-really-chunky-path-that-will-form-the-chunk-contents")) {
        autoChunk(8) {
          path(Remaining) { echoComplete }
        }
      }
      result.response.content.map(_.contentType) mustEqual Some(ContentType(`text/plain`, `ISO-8859-1`))
      result.response.content.map(_.buffer.length) mustEqual Some(0)
      result.chunks.map(_.bodyAsString).mkString("|") mustEqual
        "a-really|-chunky-|path-tha|t-will-f|orm-the-|chunk-co|ntents"
    }
  }

}