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
import HttpHeaders._
import org.specs2.mutable.Specification

class FormFieldSpec extends Specification with DefaultUnmarshallers {
  
  /*"The FormField infrastructure" should {
    "properly allow access to the fields of www-urlencoded forms" in {
      FormData(
      HttpContent(ContentType(`apple)),
        """|--XYZABC
           |--XYZABC--""".stripMargin).as[MultipartContent] mustEqual Right {
        MultipartContent(
          Vector(
            BodyPart(Nil,Some(HttpContent(ContentType(`text/plain`, Some(`US-ASCII`)), "")))
          )
        )
      }
    }
    "correctly unmarshal 'multipart/form-data' content with one part" in {
      HttpContent(ContentType(new `multipart/form-data`(Some("-"))),
        """|---
           |Content-type: text/plain; charset=UTF8
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |-----""".stripMargin).as[MultipartContent] mustEqual Right {
        MultipartContent(
          Vector(
            BodyPart(
              List(
                `Content-Type`(ContentType(`text/plain`, Some(`UTF-8`))),
                `Content-Disposition`("form-data", Map("name" -> "email"))
              ),
              Some(HttpContent(ContentType(`text/plain`, Some(`UTF-8`)), "test@there.com"))
            )
          )
        )
      }
    }
    "correctly unmarshal multipart content with two different parts" in {
      HttpContent(ContentType(new `multipart/mixed`(Some("12345"))),
        """|--12345
           |
           |first part, with a trailing linebreak
           |
           |--12345
           |Content-Type: application/octet-stream
           |Content-Transfer-Encoding: binary
           |
           |filecontent
           |--12345--""".stripMargin).as[MultipartContent] mustEqual Right {
        MultipartContent(
          Vector(
            BodyPart(Nil,
              Some(HttpContent(ContentType(`text/plain`, Some(`US-ASCII`)),
                "first part, with a trailing linebreak\n"))
            ),
            BodyPart(
              List(
                `Content-Type`(ContentType(`application/octet-stream`)),
                CustomHeader("Content-Transfer-Encoding", "binary")
              ),
              Some(HttpContent(ContentType(`application/octet-stream`), "filecontent"))
            )
          )
        )
      }
    }
    "reject illegal multipart content" in (
      HttpContent(ContentType(new `multipart/mixed`(Some("12345"))), "--noob").as[MultipartContent] mustEqual
              Left(MalformedContent("Could not parse multipart content: Missing start boundary"))
    )
  }

  "The MultipartFormDataUnmarshaller" should {
    "correctly unmarshal 'multipart/form-data' content with one element" in (
      HttpContent(ContentType(new `multipart/form-data`(Some("XYZABC"))),
        """|--XYZABC
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |--XYZABC--""".stripMargin)
      .as[MultipartFormData].right.get.parts("email").content.as[String] mustEqual Right("test@there.com")
    )
    "correctly unmarshal 'multipart/form-data' content mixed with a file" in {
      HttpContent(ContentType(new `multipart/form-data`(Some("XYZABC"))),
        """|--XYZABC
           |Content-Disposition: form-data; name="email"
           |
           |test@there.com
           |--XYZABC
           |Content-Disposition: form-data; name="userfile"; filename="test.dat"
           |Content-Type: application/octet-stream
           |Content-Transfer-Encoding: binary
           |
           |filecontent
           |--XYZABC--""".stripMargin)
      .as[MultipartFormData].right.get.parts.map {
        case (name, BodyPart(_, content)) => name + ": " + content.as[String].right.get
      }.mkString("|") mustEqual "email: test@there.com|userfile: filecontent"
    }
    "reject illegal multipart content" in (
      HttpContent(ContentType(new `multipart/form-data`(Some("XYZABC"))), "--noboundary--").as[MultipartFormData]
        mustEqual Left(MalformedContent("Could not parse multipart content: Missing start boundary"))
    )
    "reject illegal form-data content" in {
      HttpContent(ContentType(new `multipart/form-data`(Some("XYZABC"))),
        """|--XYZABC
           |content-disposition: form-data; named="email"
           |
           |test@there.com
           |--XYZABC--""".stripMargin).as[MultipartFormData] mustEqual Left {
        MalformedContent {
          "Illegal multipart/form-data content: unnamed body part (no Content-Disposition header or no 'name' parameter)"
        }
      }
    }
  }*/

}