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
import MediaTypes._
import HttpCharsets._
import test.AbstractSprayTest

class DefaultMarshallersSpec extends AbstractSprayTest {
  
  "The StringMarshaller" should {
    "encode strings to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      test(HttpRequest()) {
        _.complete("Hällö")
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
  }

  "The CharArrayMarshaller" should {
    "encode char arrays to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      test(HttpRequest()) {
        _.complete("Hällö".toCharArray)
      }.response.content mustEqual Some(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
  }

  "The NodeSeqMarshaller" should {
    "encode xml snippets to `text/xml` content in ISO-8859-1 if the client accepts it" in {
      test(HttpRequest()) {
        _.complete(<employee><nr>1</nr></employee>)
      }.response.content mustEqual
              Some(HttpContent(ContentType(`text/xml`, `ISO-8859-1`), "<employee><nr>1</nr></employee>"))
    }
  }
  
}