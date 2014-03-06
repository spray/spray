/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import ProtectedHeaderCreation.enable

class FormDataUnmarshallersSpec extends Specification {

  "The MultipartContentUnmarshaller" should {
    "correctly unmarshal 'multipart/mixed' content with one empty part" in {
      HttpEntity(`multipart/mixed` withBoundary "XYZABC",
        """|--XYZABC
           |--XYZABC--""".stripMargin).as[MultipartContent] ===
        Right(MultipartContent(Seq(BodyPart(HttpEntity(ContentType(`text/plain`, Some(`US-ASCII`)), "")))))
    }
    "correctly unmarshal 'multipart/form-data' content with one part" in {
      HttpEntity(`multipart/form-data` withBoundary "-",
        """|---
           |Content-type: text/plain; charset=UTF8
           |content-disposition: form-data; name="email"
           |
           |test@there.com
           |-----""".stripMargin).as[MultipartContent] === Right {
          MultipartContent(
            Seq(
              BodyPart(
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, "test@there.com"),
                List(
                  `Content-Disposition`("form-data", Map("name" -> "email")),
                  `Content-Type`(ContentTypes.`text/plain(UTF-8)`)))))
        }
    }
    "correctly unmarshal multipart content with two different parts" in {
      HttpEntity(`multipart/mixed` withBoundary "12345",
        """|--12345
           |
           |first part, with a trailing newline
           |
           |--12345
           |Content-Type: application/octet-stream
           |Content-Transfer-Encoding: binary
           |
           |filecontent
           |--12345--""".stripMarginWithNewline("\r\n")).as[MultipartContent] === Right {
          MultipartContent(
            Seq(
              BodyPart(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "first part, with a trailing newline\r\n")),
              BodyPart(
                HttpEntity(`application/octet-stream`, "filecontent"),
                List(
                  RawHeader("Content-Transfer-Encoding", "binary"),
                  `Content-Type`(ContentTypes.`application/octet-stream`)))))
        }
    }
    "reject illegal multipart content" in {
      val Left(MalformedContent(msg, _)) = HttpEntity(`multipart/mixed` withBoundary "12345", "--noob").as[MultipartContent]
      msg === "Missing start boundary"
    }
  }

  "The MultipartByteRangesUnmarshaller" should {
    "correctly unmarshal multipart/byteranges content with two different parts" in {
      HttpEntity(`multipart/byteranges` withBoundary "12345",
        """|--12345
          |Content-Range: bytes 0-2/26
          |Content-Type: text/plain
          |
          |ABC
          |--12345
          |Content-Range: bytes 23-25/26
          |Content-Type: text/plain
          |
          |XYZ
          |--12345--""".stripMarginWithNewline("\r\n")).as[MultipartByteRanges] === Right {
          MultipartByteRanges(
            Seq(
              BodyPart(
                HttpEntity(ContentTypes.`text/plain`, "ABC"),
                List(`Content-Type`(ContentTypes.`text/plain`), `Content-Range`(ContentRange(0, 2, 26)))),
              BodyPart(
                HttpEntity(ContentTypes.`text/plain`, "XYZ"),
                List(`Content-Type`(ContentTypes.`text/plain`), `Content-Range`(ContentRange(23, 25, 26))))))
        }
    }
  }

  "The MultipartFormDataUnmarshaller" should {
    "correctly unmarshal 'multipart/form-data' content with one element" in (
      HttpEntity(`multipart/form-data` withBoundary "XYZABC",
        """|--XYZABC
           |content-disposition: form-data; name=email
           |
           |test@there.com
           |--XYZABC--""".stripMargin).as[MultipartFormData] === Right {
          MultipartFormData(
            Map("email" -> BodyPart(
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, "test@there.com"))))
        })
    "correctly unmarshal 'multipart/form-data' content mixed with a file" in {
      HttpEntity(`multipart/form-data` withBoundary "XYZABC",
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
          case part @ BodyPart(entity, _) ⇒
            part.name.get + ": " + entity.as[String].get + part.filename.map(",filename: " + _).getOrElse("")
        }.mkString("|") === "email: test@there.com|userfile: filecontent,filename: test.dat"
    }
    "reject illegal multipart content" in {
      val Left(MalformedContent(msg, _)) = HttpEntity(`multipart/form-data` withBoundary "XYZABC", "--noboundary--").as[MultipartFormData]
      msg === "Missing start boundary"
    }
    "reject illegal form-data content" in {
      val Left(MalformedContent(msg, _)) = HttpEntity(`multipart/form-data` withBoundary "XYZABC",
        """|--XYZABC
           |content-disposition: form-data; named="email"
           |
           |test@there.com
           |--XYZABC--""".stripMargin).as[MultipartFormData]
      msg === "Illegal multipart/form-data content: unnamed body part (no Content-Disposition header or no 'name' parameter)"
    }
  }

  "The UrlEncodedFormDataUnmarshaller" should {
    "correctly unmarshal HTML form content with one element" in (
      HttpEntity(ContentType(`application/x-www-form-urlencoded`, `UTF-8`), "secret=h%C3%A4ll%C3%B6").as[FormData] ===
      Right(FormData(Map("secret" -> "hällö"))))
    "correctly unmarshal HTML form content with one element with default encoding utf-8" in (
      HttpEntity(ContentType(`application/x-www-form-urlencoded`), "secret=h%C3%A4ll%C3%B6").as[FormData] ===
      Right(FormData(Map("secret" -> "hällö"))))
    "correctly unmarshal HTML form content with three fields" in {
      HttpEntity(`application/x-www-form-urlencoded`, "email=test%40there.com&password=&username=dirk").as[FormData] ===
        Right(FormData(Map("email" -> "test@there.com", "password" -> "", "username" -> "dirk")))
    }
    "be lenient on empty key/value pairs" in {
      HttpEntity(`application/x-www-form-urlencoded`, "&key=value&&key2=&").as[FormData] ===
        Right(FormData(Map("" -> "", "key" -> "value", "key2" -> "")))
    }
    "reject illegal form content" in {
      val Left(MalformedContent(msg, _)) = HttpEntity(`application/x-www-form-urlencoded`, "key=really=not_good").as[FormData]
      msg === "Illegal form content, unexpected character '=' at position 10: \nkey=really=not_good\n          ^\n"
    }
  }
}
