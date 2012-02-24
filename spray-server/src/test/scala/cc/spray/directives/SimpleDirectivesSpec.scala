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
import HttpHeaders._
import test.AbstractSprayTest

class SimpleDirectivesSpec extends AbstractSprayTest {

  "get" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        get { completeWith(Ok) }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        get { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "The 'host' directive" should {
    "in its simple String form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("spray.com") { completeWith(Ok) }
        }.handled must beFalse
      }
      "let requests to matching hosts pass" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("spray.cc") { completeWith(Ok) }
        }.response mustEqual Ok
      }
    }
    "in its simple RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("hairspray.*".r) { _ => completeWith(Ok) }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("spra.*".r) { echoComplete }
        }.response.content.as[String] mustEqual Right("spray.cc")
      }
    }
    "in its group RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("hairspray(.*)".r) { _ => completeWith(Ok) }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(headers = Host("spray.cc") :: Nil)) {
          host("spra(.*)".r) { echoComplete }
        }.response.content.as[String] mustEqual Right("y.cc")
      }
    }
  }

  "The 'cookie' directive" should {
    "extract the respectively named cookie" in {
      test(HttpRequest(headers = Cookie(HttpCookie("fancy", "pants")) :: Nil)) {
        cookie("fancy") { echoComplete }
      }.response.content.as[String] mustEqual Right("fancy=\"pants\"")
    }
    "reject the request if the cookie is not present" in {
      test(HttpRequest()) {
        cookie("fancy") { echoComplete }
      }.rejections mustEqual Set(MissingCookieRejection("fancy"))
    }
  }

}