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

  protected class FixedRandom extends Random {
    override def nextBytes(array: Array[Byte]): Unit = "my-stable-boundary".getBytes("UTF-8").copyToArray(array)
  }
  override protected val multipartBoundaryRandom = new FixedRandom // fix for stable value

  sequential // required for stable random sequence

  "The MultipartContentMarshaller" should {

    "correctly marshal to multipart content with one empty part" in {
      marshal(MultipartContent(Seq(BodyPart("")))) === Right {
        HttpEntity(
          contentType = ContentType(`multipart/mixed` withBoundary randomBoundary),
          string = result {
            s"""|--$randomBoundary
                |--$randomBoundary--"""
          })
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
        HttpEntity(
          contentType = ContentType(`multipart/mixed` withBoundary randomBoundary),
          string = result {
            s"""|--$randomBoundary
                |Content-Disposition: form-data; name=email
                |Content-Type: text/plain; charset=UTF-8
                |
                |test@there.com
                |--$randomBoundary--"""
          })
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
        HttpEntity(
          contentType = ContentType(`multipart/mixed` withBoundary randomBoundary),
          string = result {
            s"""|--$randomBoundary
                |Content-Type: text/plain; charset=US-ASCII
                |
                |first part, with a trailing linebreak
                |
                |--$randomBoundary
                |Content-Transfer-Encoding: binary
                |Content-Type: application/octet-stream
                |
                |filecontent
                |--$randomBoundary--"""
          })
      }
    }
  }

  "The MultipartFormDataMarshaller" should {

    "correctly marshal 'multipart/form-data' with two fields" in {
      marshal(MultipartFormData(ListMap("surname" -> BodyPart("Mike"), "age" -> BodyPart(marshal(<int>42</int>).get)))) ===
        Right {
          HttpEntity(
            contentType = ContentType(`multipart/form-data` withBoundary randomBoundary),
            string = result {
              s"""|--$randomBoundary
                  |Content-Disposition: form-data; name=surname
                  |Content-Type: text/plain; charset=UTF-8
                  |
                  |Mike
                  |--$randomBoundary
                  |Content-Disposition: form-data; name=age
                  |Content-Type: text/xml; charset=UTF-8
                  |
                  |<int>42</int>
                  |--$randomBoundary--"""
            })
        }
    }

    "correctly marshal 'multipart/form-data' with two fields having a custom Content-Disposition" in {
      marshal(MultipartFormData(Seq(
        BodyPart(
          HttpEntity(`text/csv`, "name,age\r\n\"John Doe\",20\r\n"),
          List(`Content-Disposition`("form-data", Map("name" -> "attachment[0]", "filename" -> "attachment.csv")))),
        BodyPart(
          HttpEntity("name,age\r\n\"John Doe\",20\r\n".getBytes),
          List(
            `Content-Disposition`("form-data", Map("name" -> "attachment[1]", "filename" -> "attachment.csv")),
            RawHeader("Content-Transfer-Encoding", "binary")))))) ===
        Right {
          HttpEntity(
            contentType = ContentType(`multipart/form-data` withBoundary randomBoundary),
            string = result {
              s"""|--$randomBoundary
                  |Content-Disposition: form-data; name="attachment[0]"; filename=attachment.csv
                  |Content-Type: text/csv
                  |
                  |name,age
                  |"John Doe",20
                  |
                  |--$randomBoundary
                  |Content-Disposition: form-data; name="attachment[1]"; filename=attachment.csv
                  |Content-Transfer-Encoding: binary
                  |Content-Type: application/octet-stream
                  |
                  |name,age
                  |"John Doe",20
                  |
                  |--$randomBoundary--"""
            })
        }
    }

  }

  def result(body: String) = body.stripMarginWithNewline("\r\n")
}