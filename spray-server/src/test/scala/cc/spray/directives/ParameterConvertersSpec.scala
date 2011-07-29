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

class ParameterConvertersSpec extends AbstractSprayTest {

  "the IntParameterConverter" should {
    "extract parameter values as Int" in {
      test(HttpRequest(uri = "/?amount=123")) {
        parameter('amount.as[Int]) { echoComplete }
      }.response.content.as[String] mustEqual Right("123")
    }
    "cause a MalformedQueryParamRejection on illegal Int values" in {
      test(HttpRequest(uri = "/?amount=1x3")) {
        parameter('amount.as[Int]) { echoComplete }
      }.rejections mustEqual Set(MalformedQueryParamRejection("'1x3' is not a valid 32-bit integer value", Some("amount")))
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
        }.rejections mustEqual Set(MalformedQueryParamRejection("'x' is not a valid 32-bit integer value", Some("amount")))
      }
    }
  }

}