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

package spray.httpx.marshalling

import org.specs2.mutable.Specification
import spray.http._
import MediaTypes._


class BasicMarshallersSpec extends Specification {
  
  "The StringMarshaller" should {
    "encode strings to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      marshal("Hällö") === Right(HttpBody("Hällö"))
    }
  }

  "The CharArrayMarshaller" should {
    "encode char arrays to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      marshal("Hällö".toCharArray) === Right(HttpBody("Hällö"))
    }
  }

  "The NodeSeqMarshaller" should {
    "encode xml snippets to `text/xml` content in ISO-8859-1 if the client accepts it" in {
      marshal(<employee><nr>1</nr></employee>) === Right(HttpBody(`text/xml`, "<employee><nr>1</nr></employee>"))
    }
  }

  "The FormDataMarshaller" should {
    "properly marshal FormData instances to application/x-www-form-urlencoded entity bodies" in {
      marshal(FormData(Map("name" -> "Bob", "pass" -> "x?!54", "admin" -> ""))) ===
        Right(HttpBody(`application/x-www-form-urlencoded`, "name=Bob&pass=x%3F%2154&admin="))
    }
  }

}