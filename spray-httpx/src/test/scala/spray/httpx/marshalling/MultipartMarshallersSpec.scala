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

import java.util.Random
import org.specs2.mutable.Specification
import spray.util._
import spray.http._
import MediaTypes._
import HttpCharsets._
import HttpHeaders._


class MultipartMarshallersSpec extends Specification with MultipartMarshallers {
  override protected val multipartBoundaryRandom = new Random(0) // fix for stable value

  sequential // required for stable random sequence

  "The MultipartContentMarshaller" should {

    "correctly marshal to multipart content with one empty part" in {
      marshal(MultipartContent(Seq(BodyPart("")))) === Right {
        HttpBody(
          contentType = ContentType(new `multipart/mixed`(Some("YLQguzhR2dR6y5M9vnA5m/bJ"))),
          string = """|--YLQguzhR2dR6y5M9vnA5m/bJ
                     |--YLQguzhR2dR6y5M9vnA5m/bJ--""".stripMargin.replace(EOL, "\r\n")
        )
      }
    }

    "correctly marshal multipart content with one part" in {
      marshal {
        MultipartContent {
          Seq(
            BodyPart(
              entity = HttpBody(ContentType(`text/plain`, `UTF-8`), "test@there.com"),
              headers = `Content-Disposition`("form-data", Map("name" -> "email")) :: Nil
            )
          )
        }
      } === Right {
        HttpBody(ContentType(new `multipart/mixed`(Some("OvAdT7dw6YwDJfQdPrr4mG2n"))),
          """|--OvAdT7dw6YwDJfQdPrr4mG2n
            |Content-Disposition: form-data; name="email"
            |Content-Type: text/plain; charset=UTF-8
            |
            |test@there.com
            |--OvAdT7dw6YwDJfQdPrr4mG2n--""".stripMargin.replace(EOL, "\r\n")
        )
      }
    }

    "correctly marshal multipart content with two different parts" in {
      marshal {
        MultipartContent {
          Seq(
            BodyPart(HttpBody(ContentType(`text/plain`, Some(`US-ASCII`)), "first part, with a trailing linebreak\r\n")),
            BodyPart(
              HttpBody(ContentType(`application/octet-stream`), "filecontent"),
              RawHeader("Content-Transfer-Encoding", "binary") :: Nil
            )
          )
        }
      } === Right {
        HttpBody(ContentType(new `multipart/mixed`(Some("K81NVUvwtUAjwptiTenvnC+T"))),
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
  }

  "The MultipartFormDataMarshaller" should {

    "correctly marshal 'multipart/form-data' with two fields" in {
      marshal(MultipartFormData(Map("surname" -> BodyPart("Mike"), "age" -> BodyPart(marshal(<int>42</int>).get)))) ===
        Right {
          HttpBody(
            contentType = ContentType(new `multipart/form-data`(Some("WA+a+wgbEuEHsegF8rT18PHQ"))),
            string =  """|--WA+a+wgbEuEHsegF8rT18PHQ
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
}