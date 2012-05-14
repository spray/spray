/*
 * Copyright (C) 2011-2012 spray.cc
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
import HttpHeaders._
import test.AbstractSprayTest

class CookieDirectivesSpec extends AbstractSprayTest {

  "The 'cookie' directive" should {
    "extract the respectively named cookie" in {
      test(HttpRequest(headers = Cookie(HttpCookie("fancy", "pants")) :: Nil)) {
        cookie("fancy") {
          echoComplete
        }
      }.response.content.as[String] mustEqual Right("fancy=\"pants\"")
    }
    "reject the request if the cookie is not present" in {
      test(HttpRequest()) {
        cookie("fancy") {
          echoComplete
        }
      }.rejections mustEqual Set(MissingCookieRejection("fancy"))
    }
  }

  "The 'deleteCookie' directive" should {
    "add a respective Set-Cookie headers to successful responses" in {
      test(HttpRequest()) {
        deleteCookie("myCookie", "test.com") { completeWith(Ok) }
      }.response.toString mustEqual "HttpResponse(StatusCode(200, OK),List(Set-Cookie: myCookie=\"deleted\"; " +
        "Expires=Wed, 01 Jan 1800 00:00:00 GMT; Domain=test.com),None,HTTP/1.1)"
    }
  }

}