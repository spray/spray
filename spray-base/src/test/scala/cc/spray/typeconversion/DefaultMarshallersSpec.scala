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
      marshal("Hällö")() mustEqual Right(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
  }

  "The CharArrayMarshaller" should {
    "encode char arrays to `text/plain` content in ISO-8859-1 if the client accepts it" in {
      marshal("Hällö".toCharArray)() mustEqual
              Right(HttpContent(ContentType(`text/plain`, `ISO-8859-1`), "Hällö"))
    }
  }

  "The NodeSeqMarshaller" should {
    "encode xml snippets to `text/xml` content in ISO-8859-1 if the client accepts it" in {
      marshal(<employee><nr>1</nr></employee>)() mustEqual
              Right(HttpContent(ContentType(`text/xml`, `ISO-8859-1`), "<employee><nr>1</nr></employee>"))
    }
  }

  "The FormContentMarshaller" should {
    "properly marshal FormData instances to application/x-www-form-urlencoded entity bodies" in {
      marshal(FormData(Map("name" -> "Bob", "pass" -> "x?!54", "admin" -> "")))() mustEqual
              Right(HttpContent(ContentType(`application/x-www-form-urlencoded`, `ISO-8859-1`),
                "name=Bob&pass=x%3F%2154&admin="))
    }
  }

  def marshal[A :Marshaller](obj: A)(ctSelector: ContentTypeSelector = ct => Some(ct.withCharset(`ISO-8859-1`))) = {
    marshaller[A].apply(ctSelector) match {
      case MarshalWith(converter) => Right(converter(obj))
      case CantMarshal(onlyTo) => Left(onlyTo)
    }
  }
  
}