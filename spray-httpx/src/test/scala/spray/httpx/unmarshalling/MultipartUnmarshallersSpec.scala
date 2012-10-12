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

package spray.httpx.unmarshalling

import org.specs2.mutable.Specification
import spray.util._
import spray.http._
import MediaTypes._
import HttpCharsets._
import HttpHeaders._


class MultipartUnmarshallersSpec extends Specification  {
  
  "The MultipartContentUnmarshaller" should {
    "correctly unmarshal 'multipart/mixed' content with one empty part" in {
      HttpBody(new `multipart/mixed`(Some("XYZABC")),
        """|--XYZABC
           |--XYZABC--""".stripMargin).as[MultipartContent] ===
      Right(MultipartContent(Seq(BodyPart(HttpBody(ContentType(`text/plain`, Some(`US-ASCII`)), "")))))
    }
    "correctly unmarshal 'multipart/form-data' content with one part" in {
      HttpBody(new `multipart/form-data`(Some("-")),
        """|---
           |Content-type: text/plain; charset=UTF8
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |-----""".stripMargin).as[MultipartContent] === Right {
        MultipartContent(
          Seq(
            BodyPart(
              HttpBody(ContentType(`text/plain`, Some(`UTF-8`)), "test@there.com"),
              List(
                `Content-Type`(ContentType(`text/plain`, Some(`UTF-8`))),
                `Content-Disposition`("form-data", Map("name" -> "email"))
              )
            )
          )
        )
      }
    }
    "correctly unmarshal multipart content with two different parts" in {
      HttpBody(new `multipart/mixed`(Some("12345")),
        """|--12345
           |
           |first part, with a trailing EOL
           |
           |--12345
           |Content-Type: application/octet-stream
           |Content-Transfer-Encoding: binary
           |
           |filecontent
           |--12345--""".stripMargin).as[MultipartContent] === Right {
        MultipartContent(
          Seq(
            BodyPart(HttpBody(ContentType(`text/plain`, Some(`US-ASCII`)), "first part, with a trailing EOL" + EOL)),
            BodyPart(
              HttpBody(`application/octet-stream`, "filecontent"),
              List(
                `Content-Type`(ContentType(`application/octet-stream`)),
                RawHeader("Content-Transfer-Encoding", "binary")
              )
            )
          )
        )
      }
    }
    "reject illegal multipart content" in {
      val Left(MalformedContent(msg, _)) = HttpBody(new `multipart/mixed`(Some("12345")), "--noob").as[MultipartContent]
      msg === "Missing start boundary"
    }
  }

  "The MultipartFormDataUnmarshaller" should {
    "correctly unmarshal 'multipart/form-data' content with one element" in (
      HttpBody(new `multipart/form-data`(Some("XYZABC")),
        """|--XYZABC
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |--XYZABC--""".stripMargin).as[MultipartFormData] === Right {
        MultipartFormData(
          Map("email" -> BodyPart(
            HttpBody(ContentType(`text/plain`, `US-ASCII`), "test@there.com"),
            List(`Content-Disposition`("form-data", Map("name" -> "email")))
          )))
      }
    )
    "correctly unmarshal 'multipart/form-data' content mixed with a file" in {
      HttpBody(new `multipart/form-data`(Some("XYZABC")),
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
           |--XYZABC--""".stripMargin).as[MultipartFormData].get.fields.map {
        case (name, BodyPart(entity, _)) => name + ": " + entity.as[String].get
      }.mkString("|") === "email: test@there.com|userfile: filecontent"
    }
    "reject illegal multipart content" in {
      val Left(MalformedContent(msg, _)) = HttpBody(new `multipart/form-data`(Some("XYZABC")), "--noboundary--").as[MultipartFormData]
      msg === "Missing start boundary"
    }
    "reject illegal form-data content" in {
      val Left(MalformedContent(msg, _)) = HttpBody(new `multipart/form-data`(Some("XYZABC")),
        """|--XYZABC
           |content-disposition: form-data; named="email"
           |
           |test@there.com
           |--XYZABC--""".stripMargin).as[MultipartFormData]
      msg === "Illegal multipart/form-data content: unnamed body part (no Content-Disposition header or no 'name' parameter)"
    }
  }

}