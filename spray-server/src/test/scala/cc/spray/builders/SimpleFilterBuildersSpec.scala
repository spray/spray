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
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpHeaders._
import MediaTypes._
import test.AbstractSprayTest
import marshalling.DefaultUnmarshallers._

class SimpleFilterBuildersSpec extends AbstractSprayTest {

  "get" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        get { completeOk }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        get { completeOk }
      }.response mustEqual Ok
    }
  }
  
  "The 'host' directive" should {
    "in its simple String form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spray.com") { completeOk }
        }.handled must beFalse
      }
      "let requests to matching hosts pass" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spray.cc") { completeOk }
        }.response mustEqual Ok
      }
    }
    "in its simple RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("hairspray.*".r) { _ => completeOk }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spra.*".r) { host => _.complete(host) }
        }.response.content.as[String] mustEqual Right("spray.cc")
      }
    }
    "in its group RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("hairspray(.*)".r) { _ => completeOk }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spra(.*)".r) { host => _.complete(host) }
        }.response.content.as[String] mustEqual Right("y.cc")
      }
    }
  }

}