/*
 * Copyright (C) 2011-2012 spray.cc
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
import xml.NodeSeq
import org.specs2.mutable.Specification

class DefaultUnmarshallersSpec extends Specification with DefaultUnmarshallers {
  
  "The StringUnmarshaller" should {
    "decode `text/plain` content in ISO-8859-1 to Strings" in {
      HttpContent("Hällö").as[String] mustEqual Right("Hällö")
    }
  }

  "The CharArrayUnmarshaller" should {
    "decode `text/plain` content in ISO-8859-1 to char arrays" in {
      HttpContent("Hällö").as[Array[Char]].right.get.mkString mustEqual "Hällö"
    }
  }

  "The NodeSeqUnmarshaller" should {
    "decode `text/xml` content in ISO-8859-1 to NodeSeqs" in {
      HttpContent(ContentType(`text/xml`, `ISO-8859-1`), "<int>Hällö</int>").as[NodeSeq].right.get.text mustEqual "Hällö"
    }
  }

  "The FormContentUnmarshaller" should {
    "correctly unmarshal HTML form content with one element" in (
      HttpContent(ContentType(`application/x-www-form-urlencoded`, `UTF-8`), "secret=h%C3%A4ll%C3%B6").as[FormData]
        mustEqual Right(FormData(Map("secret" -> "hällö")))
    )
    "correctly unmarshal HTML form content with three fields" in {
      HttpContent(ContentType(`application/x-www-form-urlencoded`, `ISO-8859-1`),
        "email=test%40there.com&password=&username=dirk").as[FormData] mustEqual
              Right(FormData(Map("email" -> "test@there.com", "password" -> "", "username" -> "dirk")))
    }
    "be lenient on empty key/value pairs" in {
      HttpContent(ContentType(`application/x-www-form-urlencoded`, `ISO-8859-1`),
        "&key=value&&key2=&").as[FormData] mustEqual Right(FormData(Map("key" -> "value", "key2" -> "")))
    }
    "reject illegal form content" in (
      HttpContent(ContentType(`application/x-www-form-urlencoded`, `ISO-8859-1`),
        "key=really=not_good").as[FormData] mustEqual
              Left(MalformedContent("'key=really=not_good' is not a valid form content: " +
                "'key=really=not_good' does not constitute valid key=value pair"))
    )
  }

}