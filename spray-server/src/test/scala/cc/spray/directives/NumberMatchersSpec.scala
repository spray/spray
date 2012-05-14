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
import test.AbstractSprayTest

class NumberMatchersSpec extends AbstractSprayTest {

  "the LongNumber PathMatcher" should {
    "properly extract digit sequences at the path end into a Long" in {
      test(HttpRequest(uri = "/id/23")) {
        path("id" / LongNumber) { echoComplete }
      }.response.content.as[String] mustEqual Right("23")
    }
    "properly extract digit sequences in the middle of the path into a Long" in {
      test(HttpRequest(uri = "/id/25576577yes")) {
        path("id" / LongNumber ~ "yes") { echoComplete }
      }.response.content.as[String] mustEqual Right("25576577")
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / LongNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject non-digit matches" in {
      test(HttpRequest(uri = "/id/xyz")) {
        path("id" / LongNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject digit sequences representing numbers greater than Long.MaxValue" in {
      test(HttpRequest(uri = "/id/9223372036854775808")) {  // Long.MaxValue + 1
        path("id" / LongNumber) { echoComplete }
      }.handled must beFalse
    }
  }
  
  "the HexIntNumber PathMatcher" should {
    "properly extract hex digit sequences at the path end into an Int" in {
      test(HttpRequest(uri = "/id/1A2bc3")) {
        path("id" / HexIntNumber) { echoComplete }
      }.response.content.as[String] mustEqual Right("1715139")
    }
    "properly extract digit sequences in the middle of the path into an Int" in {
      test(HttpRequest(uri = "/id/7fffffffyes")) {
        path("id" / HexIntNumber ~ "yes") { echoComplete }
      }.response.content.as[String] mustEqual Right(Int.MaxValue.toString)
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / HexIntNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject non-digit matches" in {
      test(HttpRequest(uri = "/id/xaz")) {
        path("id" / HexIntNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject digit sequences representing numbers greater than Int.MaxValue" in {
      test(HttpRequest(uri = "/id/80000000")) {
        path("id" / HexIntNumber) { echoComplete }
      }.handled must beFalse
    }
  }
  
  "the DoubleNumber PathMatcher" should {
    "properly extract double representations at the path end into a Double" in {
      test(HttpRequest(uri = "/id/1.23")) {
        path("id" / DoubleNumber) { echoComplete }
      }.response.content.as[String] mustEqual Right("1.23")
    }
    "properly extract double representations in the middle of the path into a Double" in {
      test(HttpRequest(uri = "/id/-5.yes")) {
        path("id" / DoubleNumber ~ "yes") { echoComplete }
      }.response.content.as[String] mustEqual Right("-5.0")
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / DoubleNumber) {
          echoComplete
        }
      }.handled must beFalse
    }
    "reject non-digit matches" in {
      test(HttpRequest(uri = "/id/+-5")) {
        path("id" / DoubleNumber) {
          echoComplete
        }
      }.handled must beFalse
    }
  }

}