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
package typeconversion

import http._
import MediaTypes._
import HttpCharsets._
import org.specs2.mutable.Specification

class DefaultMarshallersSpec extends Specification with DefaultMarshallers {
  
  "The StringMarshaller" should {
    "encode strings to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      "Hällö".toHttpContent mustEqual HttpContent("Hällö")
    }
  }

  "The CharArrayMarshaller" should {
    "encode char arrays to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      "Hällö".toCharArray.toHttpContent mustEqual HttpContent("Hällö")
    }
  }

  "The NodeSeqMarshaller" should {
    "encode xml snippets to `text/xml` content in ISO-8859-1 if the client accepts it" in {
      <employee><nr>1</nr></employee>.toHttpContent mustEqual
              HttpContent(ContentType(`text/xml`), "<employee><nr>1</nr></employee>")
    }
  }

  "The FormDataMarshaller" should {
    "properly marshal FormData instances to application/x-www-form-urlencoded entity bodies" in {
      FormData(Map("name" -> "Bob", "pass" -> "x?!54", "admin" -> "")).toHttpContent mustEqual
              HttpContent(ContentType(`application/x-www-form-urlencoded`), "name=Bob&pass=x%3F%2154&admin=")
    }
  }

}