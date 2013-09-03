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

import java.util.Random
import scala.collection.immutable.ListMap
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
        HttpEntity(
          contentType = ContentType(`multipart/mixed` withBoundary "YLQguzhR2dR6y5M9vnA5m-bJ"),
          string = """|--YLQguzhR2dR6y5M9vnA5m-bJ
                     |--YLQguzhR2dR6y5M9vnA5m-bJ--""".stripMargin.replace(EOL, "\r\n"))
      }
    }

    "correctly marshal multipart content with one part" in {
      marshal {
        MultipartContent {
          Seq(
            BodyPart(
              entity = HttpEntity(ContentType(`text/plain`, `UTF-8`), "test@there.com"),
              headers = `Content-Disposition`("form-data", Map("name" -> "email")) :: Nil))
        }
      } === Right {
        HttpEntity(ContentType(`multipart/mixed` withBoundary "OvAdT7dw6YwDJfQdPrr4mG2n"),
          """|--OvAdT7dw6YwDJfQdPrr4mG2n
            |Content-Disposition: form-data; name=email
            |Content-Type: text/plain; charset=UTF-8
            |
            |test@there.com
            |--OvAdT7dw6YwDJfQdPrr4mG2n--""".stripMargin.replace(EOL, "\r\n"))
      }
    }

    "correctly marshal multipart content with two different parts" in {
      marshal {
        MultipartContent {
          Seq(
            BodyPart(HttpEntity(ContentType(`text/plain`, Some(`US-ASCII`)), "first part, with a trailing linebreak\r\n")),
            BodyPart(
              HttpEntity(ContentType(`application/octet-stream`), "filecontent"),
              RawHeader("Content-Transfer-Encoding", "binary") :: Nil))
        }
      } === Right {
        HttpEntity(ContentType(`multipart/mixed` withBoundary "K81NVUvwtUAjwptiTenvnC+T"),
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
            |--K81NVUvwtUAjwptiTenvnC+T--""".stripMargin.replace(EOL, "\r\n"))
      }
    }
  }

  "The MultipartFormDataMarshaller" should {

    "correctly marshal 'multipart/form-data' with two fields" in {
      marshal(MultipartFormData(ListMap("surname" -> BodyPart("Mike"), "age" -> BodyPart(marshal(<int>42</int>).get)))) ===
        Right {
          HttpEntity(
            contentType = ContentType(`multipart/form-data` withBoundary "WA+a+wgbEuEHsegF8rT18PHQ"),
            string = """|--WA+a+wgbEuEHsegF8rT18PHQ
                        |Content-Disposition: form-data; name=surname
                        |Content-Type: text/plain
                        |
                        |Mike
                        |--WA+a+wgbEuEHsegF8rT18PHQ
                        |Content-Disposition: form-data; name=age
                        |Content-Type: text/xml
                        |
                        |<int>42</int>
                        |--WA+a+wgbEuEHsegF8rT18PHQ--""".stripMargin.replace(EOL, "\r\n"))
        }
    }

    "correctly marshal 'multipart/form-data' with two fields having a custom Content-Disposition" in {
      marshal(MultipartFormData(ListMap(
        "first-attachment" -> BodyPart(
          HttpEntity(`text/csv`, "name,age\r\n\"John Doe\",20\r\n"),
          List(`Content-Disposition`("form-data", Map("name" -> "attachment", "filename" -> "attachment.csv")))),
        "second-attachment" -> BodyPart(
          HttpEntity("name,age\r\n\"John Doe\",20\r\n".getBytes),
          List(
            `Content-Disposition`("form-data", Map("name" -> "attachment", "filename" -> "attachment.csv")),
            RawHeader("Content-Transfer-Encoding", "binary")))))) ===
        Right {
          HttpEntity(
            contentType = ContentType(`multipart/form-data` withBoundary "D2JjRnCSHFBYZ-8g9qgzXpiv"),
            string = """|--D2JjRnCSHFBYZ-8g9qgzXpiv
                       |Content-Disposition: form-data; name=attachment; filename=attachment.csv
                       |Content-Type: text/csv
                       |
                       |name,age
                       |"John Doe",20
                       |
                       |--D2JjRnCSHFBYZ-8g9qgzXpiv
                       |Content-Disposition: form-data; name=attachment; filename=attachment.csv
                       |Content-Transfer-Encoding: binary
                       |Content-Type: application/octet-stream
                       |
                       |name,age
                       |"John Doe",20
                       |
                       |--D2JjRnCSHFBYZ-8g9qgzXpiv--""".stripMargin.replace(EOL, "\r\n"))
        }
    }

  }
}