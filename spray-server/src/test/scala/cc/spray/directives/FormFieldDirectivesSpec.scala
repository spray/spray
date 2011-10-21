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
package directives

import http._
import typeconversion._
import test.AbstractSprayTest

class FormFieldDirectivesSpec extends AbstractSprayTest with Directives {

  val urlEncodedForm = FormData(Map("firstName" -> "Mike", "age" -> "42"))
  val multipartForm = MultipartFormData(Map("firstName" -> BodyPart("Mike"), "age" -> BodyPart(<int>42</int>)))

  "The 'formFields' extraction directive" should {
    "properly extract the value of www-urlencoded form fields" in {
      test(HttpRequest(content = Some(urlEncodedForm.toHttpContent))) {
        formFields('firstName, "age".as[Int], 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.response.content.as[String] mustEqual Right("Mike42Nonefalse")
    }
    "properly extract the value of www-urlencoded form fields when an explicit deserializer is given" in {
      test(HttpRequest(content = Some(urlEncodedForm.toHttpContent))) {
        formFields('firstName, "age".as(HexInt), 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.response.content.as[String] mustEqual Right("Mike66Nonefalse")
    }
    "properly extract the value of multipart form fields" in {
      test(HttpRequest(content = Some(multipartForm.toHttpContent))) {
        formFields('firstName, "age", 'sex?, "VIP" ? (<b>yes</b>:xml.NodeSeq)) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.response.content.as[String] mustEqual Right("Mike<int>42</int>None<b>yes</b>")
    }
    "reject the request with a MissingFormFieldRejection if a required form field is missing" in {
      test(HttpRequest(content = Some(urlEncodedForm.toHttpContent))) {
        formFields('firstName, "age", 'sex, "VIP" ? false) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.rejections mustEqual Set(MissingFormFieldRejection("sex"))
    }
    "create a proper error message if only a multipart unmarshaller is available for a www-urlencoded field" in {
      test(HttpRequest(content = Some(urlEncodedForm.toHttpContent))) {
        formFields('firstName, "age", 'sex?, "VIP" ? (<b>yes</b>:xml.NodeSeq)) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.rejections mustEqual Set(UnsupportedRequestContentTypeRejection(
        "Field 'VIP' can only be read from 'application/x-www-form-urlencoded' form content"))
    }
    "create a proper error message if only a urlencoded deserializer is available for a multipart field" in {
      test(HttpRequest(content = Some(multipartForm.toHttpContent))) {
        formFields('firstName, "age", 'sex?, "VIP" ? false) { (firstName, age, sex, vip) =>
          _.complete(firstName + age + sex + vip)
        }
      }.rejections mustEqual Set(UnsupportedRequestContentTypeRejection(
        "Field 'VIP' can only be read from 'multipart/form-data' form content"))
    }
  }

}