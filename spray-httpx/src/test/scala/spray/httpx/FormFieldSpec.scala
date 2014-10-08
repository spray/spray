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

package spray.httpx

import scala.xml.NodeSeq
import org.specs2.mutable.Specification
import spray.util._
import spray.http._

class FormFieldSpec extends Specification {
  import spray.httpx.marshalling._
  import spray.httpx.unmarshalling._

  val formData =
    FormData(Map("surname" -> "Smith", "age" -> "42"))
  val multipartFormData =
    MultipartFormData(Seq(
      BodyPart("Smith", Seq(HttpHeaders.`Content-Disposition`("form-data", Map("name" -> "surname")))),
      BodyPart(marshal(<int>42</int>).get, Seq(HttpHeaders.`Content-Disposition`("form-data", Map("filename" -> "age.xml", "name" -> "age"))))))

  "The FormField infrastructure" should {
    "properly allow access to the fields of www-urlencoded forms" in {
      marshal(formData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("surname").as[String]) === Right("Smith")

      marshal(formData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("age").as[Int]) === Right(42)
    }

    "properly allow access to the fields of www-urlencoded forms containing special chars" in {
      marshal(FormData(Map("name" -> "Smith&Wesson")))
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("name").as[String]) === Right("Smith&Wesson")
    }

    "properly encode the fields of www-urlencoded forms containing special chars" in {
      marshal(FormData(Map("name" -> "Smith+Wesson; hopefully!")))
        .right.map(_.asString) === Right("name=Smith%2BWesson%3B+hopefully%21")
    }

    "properly allow access to the fields of multipart/form-data forms" in {
      marshal(multipartFormData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("surname").as[String]) === Right("Smith")

      marshal(multipartFormData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("age").as[NodeSeq]) === Right(<int>42</int>)
    }

    "return an error when accessing a field of www-urlencoded forms for which no FromStringOptionDeserializer is available" in {
      marshal(formData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("age").as[NodeSeq]) ===
        Left(UnsupportedContentType("Expected 'text/xml' or 'application/xml' or 'text/html' or 'application/xhtml+xml' " +
          "but tried to read from application/x-www-form-urlencoded encoded field 'age' which provides only text/plain values."))
    }

    "return an error when accessing a field of multipart forms for which no Unmarshaller is available" in {
      marshal(multipartFormData)
        .flatMap(_.as[HttpForm])
        .flatMap(_.field("age").as[Int]) ===
        Left(UnsupportedContentType("Field 'age' can only be read from 'application/x-www-form-urlencoded' form content but was 'text/xml'"))
    }
  }

}
