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

package spray.httpx.marshalling

import org.specs2.mutable.Specification
import spray.http._
import MediaTypes._
import HttpCharsets._

class BasicMarshallersSpec extends Specification {

  "The StringMarshaller" should {
    "encode strings to `text/plain` content in UTF-8 if the client accepts it" in {
      marshal("Ha“llo") === Right(HttpEntity("Ha“llo"))
    }
  }

  "The CharArrayMarshaller" should {
    "encode char arrays to `text/plain` content in UTF-8 if the client accepts it" in {
      marshal("Ha“llo".toCharArray) === Right(HttpEntity("Ha“llo"))
    }
  }

  "The NodeSeqMarshaller" should {
    "encode xml snippets to `text/xml` content in UTF-8 if the client accepts it" in {
      marshal(<employee><nr>Ha“llo</nr></employee>) === Right(HttpEntity(ContentType(`text/xml`, `UTF-8`),
        "<employee><nr>Ha“llo</nr></employee>"))
    }
  }

  "The FormDataMarshaller" should {
    "properly marshal FormData instances to application/x-www-form-urlencoded entity bodies" in {
      marshal(FormData(Map("name" -> "Bob", "pass" -> "hällo", "admin" -> ""))) ===
        Right(HttpEntity(ContentType(`application/x-www-form-urlencoded`, `UTF-8`), "name=Bob&pass=h%E4llo&admin"))
    }
  }

}