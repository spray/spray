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
    "extract the value of required www-urlencoded form fields" in {
      test(HttpRequest(content = Some(urlEncodedForm.toHttpContent))) {
        formFields('firstName, "age", 'sex?) { (firstName, age, sex) =>
          _.complete(firstName + name + sex)
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    /*"ignore additional parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen&age=29")) {
        path("person") {
          parameters("name", 'FirstName) { (name, firstName) =>
            _.complete(firstName + name)
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "reject the request with a MissingQueryParamRejection if a required parameters is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&sex=female")) {
        path("person") {
          parameters('name, 'FirstName, 'age) { (name, firstName, age) =>
            completeOk
          }
        }
      }.rejections mustEqual Set(MissingQueryParamRejection("FirstName"))
    }
    "supply the default value if an optional parameter is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name"?, 'FirstName, 'age ? "29", 'eyes?) { (name, firstName, age, eyes) =>
            _.complete(firstName + name + age + eyes)
          }
        }
      }.response.content.as[String] mustEqual Right("EllenSome(Parsons)29None")
    }*/
  }

}