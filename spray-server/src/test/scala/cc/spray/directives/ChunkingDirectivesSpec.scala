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

class ChunkingDirectivesSpec extends AbstractSprayTest {

  "Value extraction as case class" should {
    "work for 1 parameter case classes from string extractions" in {
      val result = test(HttpRequest(uri = "/a-really-chunky-path-that-will-form-the-chunk-contents")) {
        autoChunk(8) {
          path(Remaining) { echoComplete }
        }
      }
      result.response.content.as[String] mustEqual Right("a-really")
      result.chunks.map(_.bodyAsString).mkString("|") mustEqual "-chunky-|path-tha|t-will-f|orm-the-|chunk-co|ntents"
    }
  }

}