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
import HttpMethods._
import test.AbstractSprayTest

class ParameterDirectivesSpec extends AbstractSprayTest {

  "The 'parameter' extraction directive" should {
    "extract the value of given required parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name", 'FirstName) { (name, firstName) =>
            get { _.complete(firstName + name) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "ignore additional parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen&age=29")) {
        path("person") {
          parameters("name", 'FirstName) { (name, firstName) =>
            get { _.complete(firstName + name) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "reject the request with a MissingQueryParamRejection if a required parameters is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&sex=female")) {
        path("person") {
          parameters('name, 'FirstName, 'age) { (name, firstName, age) =>
            get { _ => fail("Should not run") }
          }
        }
      }.rejections mustEqual Set(MissingQueryParamRejection("FirstName"))
    }
    "supply the default value if an optional parameter is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name"?, 'FirstName, 'age ? "29", 'eyes?) { (name, firstName, age, eyes) =>
            get { _.complete(firstName + name + age + eyes) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenSome(Parsons)29None")
    }
  }
  
  "The 'parameter' requirement directive" should {
    "block requests that do not contain the required parameter" in {
      test(HttpRequest(uri = "/person?age=19")) { 
        parameter('nose ! "large") { _ => fail("Should not run") }
      }.handled must beFalse
    }
    "block requests that contain the required parameter but with an unmatching value" in {
      test(HttpRequest(uri = "/person?age=19&nose=small")) { 
        parameter('nose ! "large") { _ => fail("Should not run") }
      }.handled must beFalse
    }
    "let requests pass that contain the required parameter with its required value" in {
      test(HttpRequest(uri = "/person?nose=large&eyes=blue")) {
        path("person") {
          parameter('nose ! "large") { _.complete("yes") }
        }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "be useable for method tunneling" in {
      val route = {
        path("person") {
          (post | parameter('method ! "post")) {
            _.complete("POST")
          } ~
          get { _.complete("GET") }
        }
      }
      test(HttpRequest(uri = "/person?method=post")) {
        route 
      }.response.content.as[String] mustEqual Right("POST")
      test(HttpRequest(POST, uri = "/person")) {
        route 
      }.response.content.as[String] mustEqual Right("POST")
      test(HttpRequest(uri = "/person")) {
        route 
      }.response.content.as[String] mustEqual Right("GET")
    }
  }

}