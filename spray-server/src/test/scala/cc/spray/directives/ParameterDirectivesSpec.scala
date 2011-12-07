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

  "when used with 'as[Int]' the parameter directive" should {
    "extract parameter values as Int" in {
      test(HttpRequest(uri = "/?amount=123")) {
        parameter('amount.as[Int]) { echoComplete }
      }.response.content.as[String] mustEqual Right("123")
    }
    "cause a MalformedQueryParamRejection on illegal Int values" in {
      test(HttpRequest(uri = "/?amount=1x3")) {
        parameter('amount.as[Int]) { echoComplete }
      }.rejections mustEqual Set(MalformedQueryParamRejection("'1x3' is not a valid 32-bit integer value", "amount"))
    }
    "supply typed default values" in {
      test(HttpRequest(uri = "/")) {
        parameter('amount ? 45) { echoComplete }
      }.response.content.as[String] mustEqual Right("45")
    }
    "create typed optional parameters that" in {
      "extract Some(value) when present" in {
        test(HttpRequest(uri = "/?amount=12")) {
          parameter("amount".as[Int]?) { echoComplete }
        }.response.content.as[String] mustEqual Right("Some(12)")
      }
      "extract None when not present" in {
        test(HttpRequest(uri = "/")) {
          parameter("amount".as[Int]?) { echoComplete }
        }.response.content.as[String] mustEqual Right("None")
      }
      "cause a MalformedQueryParamRejection on illegal Int values" in {
        test(HttpRequest(uri = "/?amount=x")) {
          parameter("amount".as[Int]?) { echoComplete }
        }.rejections mustEqual Set(MalformedQueryParamRejection("'x' is not a valid 32-bit integer value", "amount"))
      }
    }
  }

  "when used with 'as(HexInt)' the parameter directive" should {
    "extract parameter values as Int" in {
      test(HttpRequest(uri = "/?amount=1f")) {
        parameter('amount.as(HexInt)) { echoComplete }
      }.response.content.as[String] mustEqual Right("31")
    }
    "cause a MalformedQueryParamRejection on illegal Int values" in {
      test(HttpRequest(uri = "/?amount=1x3")) {
        parameter('amount.as(HexInt)) { echoComplete }
      }.rejections mustEqual Set(MalformedQueryParamRejection("'1x3' is not a valid 32-bit hexadecimal integer value", "amount"))
    }
    "supply typed default values" in {
      test(HttpRequest(uri = "/")) {
        parameter('amount.as(HexInt) ? 45) { echoComplete }
      }.response.content.as[String] mustEqual Right("45")
    }
    "create typed optional parameters that" in {
      "extract Some(value) when present" in {
        test(HttpRequest(uri = "/?amount=A")) {
          parameter("amount".as(HexInt)?) { echoComplete }
        }.response.content.as[String] mustEqual Right("Some(10)")
      }
      "extract None when not present" in {
        test(HttpRequest(uri = "/")) {
          parameter("amount".as(HexInt)?) { echoComplete }
        }.response.content.as[String] mustEqual Right("None")
      }
      "cause a MalformedQueryParamRejection on illegal Int values" in {
        test(HttpRequest(uri = "/?amount=x")) {
          parameter("amount".as(HexInt)?) { echoComplete }
        }.rejections mustEqual Set(MalformedQueryParamRejection("'x' is not a valid 32-bit hexadecimal integer value", "amount"))
      }
    }
  }

  "The 'parameters' extraction directive" should {
    "extract the value of given required parameters" in {
      test(HttpRequest(uri = "/?name=Parsons&FirstName=Ellen")) {
        parameters("name", 'FirstName) { (name, firstName) =>
          completeWith(firstName + name)
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "ignore additional parameters" in {
      test(HttpRequest(uri = "/?name=Parsons&FirstName=Ellen&age=29")) {
        parameters("name", 'FirstName) { (name, firstName) =>
          completeWith(firstName + name)
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "reject the request with a MissingQueryParamRejection if a required parameters is missing" in {
      test(HttpRequest(uri = "/?name=Parsons&sex=female")) {
        parameters('name, 'FirstName, 'age) { (name, firstName, age) =>
          completeWith(Ok)
        }
      }.rejections mustEqual Set(MissingQueryParamRejection("FirstName"))
    }
    "supply the default value if an optional parameter is missing" in {
      test(HttpRequest(uri = "/?name=Parsons&FirstName=Ellen")) {
        parameters("name"?, 'FirstName, 'age ? "29", 'eyes?) { (name, firstName, age, eyes) =>
          completeWith(firstName + name + age + eyes)
        }
      }.response.content.as[String] mustEqual Right("EllenSome(Parsons)29None")
    }
  }

  "The 'parameter' requirement directive" should {
    "block requests that do not contain the required parameter" in {
      test(HttpRequest(uri = "/person?age=19")) { 
        parameter('nose ! "large") { completeWith(Ok) }
      }.handled must beFalse
    }
    "block requests that contain the required parameter but with an unmatching value" in {
      test(HttpRequest(uri = "/person?age=19&nose=small")) { 
        parameter('nose ! "large") { completeWith(Ok) }
      }.handled must beFalse
    }
    "let requests pass that contain the required parameter with its required value" in {
      test(HttpRequest(uri = "/person?nose=large&eyes=blue")) {
        parameter('nose ! "large") { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "be useable for method tunneling" in {
      val route = {
        path("person") {
          (post | parameter('method ! "post")) {
            completeWith("POST")
          } ~
          get { completeWith("GET") }
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