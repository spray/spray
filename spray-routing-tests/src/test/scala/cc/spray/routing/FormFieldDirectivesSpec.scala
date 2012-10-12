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

package spray.routing

import shapeless.HNil
import spray.httpx.marshalling.marshalUnsafe
import spray.httpx.unmarshalling.FromStringDeserializers.HexInt
import spray.http._


class FormFieldDirectivesSpec extends RoutingSpec {

  val nodeSeq: xml.NodeSeq = <b>yes</b>
  val urlEncodedForm = FormData(Map("firstName" -> "Mike", "age" -> "42"))
  val multipartForm = MultipartFormData {
    Map(
      "firstName" -> BodyPart("Mike"),
      "age" -> BodyPart(marshalUnsafe(<int>42</int>))
    )
  }

  "The 'formFields' extraction directive" should {
    "properly extract the value of www-urlencoded form fields (using the general HList-based variant)" in {
      Get("/", urlEncodedForm) ~> {
        formFields('firstName :: ("age".as[Int]) :: ('sex?) :: ("VIP" ? false) :: HNil) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { entityAs[String] === "Mike42Nonefalse" }
    }
    "properly extract the value of www-urlencoded form fields (using the simple non-HList-based variant)" in {
      Get("/", urlEncodedForm) ~> {
        formFields('firstName, "age".as[Int], 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { entityAs[String] === "Mike42Nonefalse" }
    }
    "properly extract the value of www-urlencoded form fields when an explicit deserializer is given" in {
      Get("/", urlEncodedForm) ~> {
        formFields('firstName, "age".as(HexInt), 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { entityAs[String] === "Mike66Nonefalse" }
    }
    "properly extract the value of multipart form fields (using the general HList-based variant)" in {
      Get("/", multipartForm) ~> {
        formFields('firstName :: "age" :: ('sex?) :: ("VIP" ? nodeSeq) :: HNil) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { entityAs[String] === "Mike<int>42</int>None<b>yes</b>" }
    }
    "properly extract the value of multipart form fields (using the simple non-HList-based variant)" in {
      Get("/", multipartForm) ~> {
        formFields('firstName, "age", 'sex?, "VIP" ? nodeSeq) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { entityAs[String] === "Mike<int>42</int>None<b>yes</b>" }
    }
    "reject the request with a MissingFormFieldRejection if a required form field is missing" in {
      Get("/", urlEncodedForm) ~> {
        formFields('firstName, "age", 'sex, "VIP" ? false) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { rejection === MissingFormFieldRejection("sex") }
    }
    "create a proper error message if only a multipart unmarshaller is available for a www-urlencoded field" in {
      Get("/", urlEncodedForm) ~> {
        formFields('firstName, "age", 'sex?, "VIP" ? nodeSeq) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { rejection === UnsupportedRequestContentTypeRejection("Field 'VIP' can only be read from " +
        "'multipart/form-data' form content") }
    }
    "create a proper error message if only a urlencoded deserializer is available for a multipart field" in {
      Get("/", multipartForm) ~> {
        formFields('firstName, "age", 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          complete(firstName + age + sex + vip)
        }
      } ~> check { rejection === UnsupportedRequestContentTypeRejection("Field 'VIP' can only be read from " +
        "'application/x-www-form-urlencoded' form content") }
    }
  }

}
