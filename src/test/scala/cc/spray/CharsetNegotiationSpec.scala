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

import http._
import org.specs.Specification
import HttpHeaders._
import MediaTypes._
import Charsets._
import test.SprayTest

class CharsetNegotiationSpec extends Specification with SprayTest with ServiceBuilder {
  
  "The framework" should {
    "encode text content using ISO-8859-1 if no Accept-Charset header is present in the request" in {
      test(HttpRequest()) {
        _.complete("Hällö")
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
    "encode text content using ISO-8859-1 if the Accept-Charset header contains '*'" in {
      test(HttpRequest(headers = List(`Accept-Charset`(`UTF-8`, `*`)))) {
        _.complete("Hällö")
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
    "encode text content using the first charset in the Accept-Charset header if '*' is not present" in {
      test(HttpRequest(headers = List(`Accept-Charset`(`UTF-8`)))) {
        _.complete("Hällö")
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `UTF-8`), "Hällö"))
    }
  }
  
}