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

class BasicDirectivesSpec extends AbstractSprayTest {

  "get | put" should {
    val getOrPut = get | put
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        getOrPut { completeWith(Ok) }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        getOrPut { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "let PUT requests pass" in {
      test(HttpRequest(PUT)) { 
        getOrPut { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "host(regex) | host(otherRegex)" should {
    "block requests not matching any of the two conditions" in {
      test(HttpRequest(headers = Host("xyz.com") :: Nil)) {
        (host("spray.*".r) | host("clay.*".r)) { _ => completeWith(Ok) }
      }.handled must beFalse
    }
    "extract the first match if it is successful" in {
      test(HttpRequest(headers = Host("www.spray.cc") :: Nil)) {
        (host("[^\\.]+.spray.cc".r) | host("spray(.*)".r)) { echoComplete }
      }.response.content.as[String] mustEqual Right("www.spray.cc")
    }
    "extract the second match if the first failed and the second is successful" in {
      test(HttpRequest(headers = Host("spray.cc", Some(8000)) :: Nil)) {
        (host("[^\\.]+.spray.cc".r) | host("spray(.*)".r)) { echoComplete }
      }.response.content.as[String] mustEqual Right(".cc")
    }
  }
  
  "host(regex) & parameters('a, 'b)" should {
    val filter = host("([^\\.]+).spray.cc".r) & parameters('a, 'b) 
    "block requests to unmatching hosts" in {
      test(HttpRequest(GET, "/?a=1&b=2", Host("www.spray.org") :: Nil)) {
        filter { (_, _, _) => completeWith(Ok) }
      }.handled must beFalse
    }
    "block requests without matching parameters" in {
      test(HttpRequest(GET, "/?a=1&c=2", Host("www.spray.cc") :: Nil)) {
        filter { (_, _, _) => completeWith(Ok) }
      }.handled must beFalse
    }
    "let matching requests pass and extract all values" in {
      test(HttpRequest(GET, "/?a=1&b=2", Host("www.spray.cc") :: Nil)) {
        filter { (host, a, b) => completeWith(host + a + b) }
      }.response.content.as[String] mustEqual Right("www12")
    }
  }
  
  "put | (get & parameter('method ! \"put\"))" should {
    val putx = put | (get & parameter('method ! "put")) 
    "block GET requests" in {
      test(HttpRequest(GET)) { 
        putx { completeWith(Ok) }
      }.handled must beFalse
    }
    "let PUT requests pass" in {
      test(HttpRequest(PUT)) { 
        putx { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "let GET requests with method tunneling pass" in {
      test(HttpRequest(GET, "/?method=put")) { 
        putx { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "block POST requests with method tunneling parameter" in {
      test(HttpRequest(POST, "/?method=put")) { 
        putx { completeWith(Ok) }
      }.handled must beFalse
    }
  }
  
  "(!get & !put)" should {
    "block GET requests" in {
      test(HttpRequest(GET)) { 
        (!get & !put) { completeWith(Ok) }
      }.handled must beFalse
    }
    "block PUT requests" in {
      test(HttpRequest(GET)) { 
        (!get & !put) { completeWith(Ok) }
      }.handled must beFalse
    }
    "let POST requests pass" in {
      test(HttpRequest(POST)) { 
        (!get & !put) { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }

}