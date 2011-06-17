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
package marshalling

import http._
import org.specs.Specification
import MediaTypes._
import HttpCharsets._
import test.SprayTest
import xml.NodeSeq

class DefaultUnmarhallersSpec extends Specification with SprayTest with Directives {
  
  "The StringUnmarshaller" should {
    "decode `text/plain` content in ISO-8859-1 to Strings" in {
      test(HttpRequest(content = Some(HttpContent("Hällö")))) {
        content(as[String]) { s => _.complete(s) }
      }.response.content.as[String] mustEqual Right("Hällö")
    }
  }

  "The CharArrayUnmarshaller" should {
    "decode `text/plain` content in ISO-8859-1 to char arrays" in {
      test(HttpRequest(content = Some(HttpContent("Hällö")))) {
        content(as[Array[Char]]) { charArray => _.complete(charArray) }
      }.response.content.as[String] mustEqual Right("Hällö")
    }
  }

  "The NodeSeqUnmarshaller" should {
    "decode `text/xml` content in ISO-8859-1 to NodeSeqs" in {
      test(HttpRequest(content = Some(HttpContent(ContentType(`text/xml`, `ISO-8859-1`), "<int>Hällö</int>")))) {
        content(as[NodeSeq]) { xml => _.complete(xml.text) }
      }.response.content.as[String] mustEqual Right("Hällö")
    }
  }

}