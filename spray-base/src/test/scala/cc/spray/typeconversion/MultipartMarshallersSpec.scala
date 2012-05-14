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
import HttpHeaders._
import org.specs2.mutable.Specification
import java.util.Random
import util.EOL

class MultipartMarshallersSpec extends Specification with DefaultMarshallers {
  override protected lazy val multipartBoundaryRandom = new Random(0) // fix for stable value

  sequential // require for stable random sequence

  "The MultipartContentMarshaller" should {
    "correctly marshal to multipart content with one empty part" in {
      MultipartContent(
        Seq(BodyPart(HttpContent(ContentType(`text/plain`, `US-ASCII`), "")))
      ).toHttpContent mustEqual HttpContent(ContentType(new `multipart/mixed`(Some("YLQguzhR2dR6y5M9vnA5m/bJ"))),
        """|--YLQguzhR2dR6y5M9vnA5m/bJ
           |--YLQguzhR2dR6y5M9vnA5m/bJ--""".stripMargin.replace(EOL, "\r\n")
      )
    }
    "correctly marshal multipart content with one part" in {
      MultipartContent(
        Seq(
          BodyPart(
            headers = `Content-Disposition`("form-data", Map("name" -> "email")) :: Nil,
            content = HttpContent(ContentType(`text/plain`, `UTF-8`), "test@there.com")
          )
        )
      ).toHttpContent mustEqual HttpContent(ContentType(new `multipart/mixed`(Some("OvAdT7dw6YwDJfQdPrr4mG2n"))),
        """|--OvAdT7dw6YwDJfQdPrr4mG2n
           |Content-Disposition: form-data; name="email"
           |Content-Type: text/plain; charset=UTF-8
           |
           |test@there.com
           |--OvAdT7dw6YwDJfQdPrr4mG2n--""".stripMargin.replace(EOL, "\r\n")
      )
    }
    "correctly marshal multipart content with two different parts" in {
      MultipartContent(
        Seq(
          BodyPart(Nil, Some(HttpContent(ContentType(`text/plain`, Some(`US-ASCII`)),
            "first part, with a trailing linebreak\r\n"))),
          BodyPart(
            CustomHeader("Content-Transfer-Encoding", "binary") :: Nil,
            HttpContent(ContentType(`application/octet-stream`), "filecontent")
          )
        )
      ).toHttpContent mustEqual HttpContent(ContentType(new `multipart/mixed`(Some("K81NVUvwtUAjwptiTenvnC+T"))),
        """|--K81NVUvwtUAjwptiTenvnC+T
           |Content-Type: text/plain; charset=US-ASCII
           |
           |first part, with a trailing linebreak
           |
           |--K81NVUvwtUAjwptiTenvnC+T
           |Content-Transfer-Encoding: binary
           |Content-Type: application/octet-stream
           |
           |filecontent
           |--K81NVUvwtUAjwptiTenvnC+T--""".stripMargin.replace(EOL, "\r\n")
      )
    }
  }

  "The MultipartFormDataMarshaller" should {
    "correctly marshal 'multipart/form-data' with two fields" in {
      MultipartFormData(Map("surname" -> BodyPart("Mike"), "age" -> BodyPart(<int>42</int>))).toHttpContent mustEqual
      HttpContent(ContentType(new `multipart/form-data`(Some("WA+a+wgbEuEHsegF8rT18PHQ"))),
        """|--WA+a+wgbEuEHsegF8rT18PHQ
           |Content-Disposition: form-data; name="surname"
           |Content-Type: text/plain
           |
           |Mike
           |--WA+a+wgbEuEHsegF8rT18PHQ
           |Content-Disposition: form-data; name="age"
           |Content-Type: text/xml
           |
           |<int>42</int>
           |--WA+a+wgbEuEHsegF8rT18PHQ--""".stripMargin.replace(EOL, "\r\n")
      )
    }
  }
}