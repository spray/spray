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
import StatusCodes._
import test.AbstractSprayTest

class PathDirectivesSpec extends AbstractSprayTest {

  "routes created with the path(string) combinator" should {
    "block completely unmatching requests" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        path("hello") { completeWith(Ok) }
      }.handled must beFalse
    }
    "block prefix requests" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        path("noway/this") { completeWith(Ok) }
      }.handled must beFalse
    }
    "let fully matching requests pass and clear the RequestContext.unmatchedPath" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        path("noway/this/works") { ctx => ctx.complete(ctx.unmatchedPath) }
      }.response.content.as[String] mustEqual Right("")
    }
    "be stackable within one single path(...) combinator" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        path("noway" / "this" / "works") {
          ctx => ctx.complete(ctx.unmatchedPath)
        }
      }.response.content.as[String] mustEqual Right("")
    }
    "implicitly match trailing slashes" in {
      test(HttpRequest(uri = "/works/")) {
        path("works") { completeWith(Ok) }
      }.response mustEqual Ok
      test(HttpRequest(uri = "")) {
        path("") { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "routes created with the pathPrefix(string) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        pathPrefix("hello") { completeWith(Ok) }
      }.handled must beFalse
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        pathPrefix("noway") { ctx => ctx.complete(ctx.unmatchedPath) }
      }.response.content.as[String] mustEqual Right("/this/works")
    }
    "be stackable" in {
      "within one single pathPrefix(...) combinator" in {
        test(HttpRequest(uri = "/noway/this/works")) {
          pathPrefix("noway" / "this" / "works" ~ Remaining) {
            echoComplete
          }
        }.response.content.as[String] mustEqual Right("")
      }
      "when nested" in {
        test(HttpRequest(uri = "/noway/this/works")) {
          pathPrefix("noway") {
            pathPrefix("this") { ctx => ctx.complete(ctx.unmatchedPath) }
          }
        }.response.content.as[String] mustEqual Right("/works")
      }
    }
  }
  
  "routes created with the pathPrefix(regex) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(uri = "/noway/this/works")) {
        pathPrefix("\\d".r) { _ => completeWith(Ok) }
      }.handled must beFalse
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex" in {
        test(HttpRequest(uri = "/noway/this/works")) {
          pathPrefix("no[^/]+".r) { capture =>
            ctx => ctx.complete(capture + ":" + ctx.unmatchedPath)
          }
        }.response.content.as[String] mustEqual Right("noway:/this/works")
      }
      "when the regex is a group regex" in {
        test(HttpRequest(uri = "/noway/this/works")) {
          pathPrefix("no([^/]+)".r) { capture =>
            ctx => ctx.complete(capture + ":" + ctx.unmatchedPath)
          }
        }.response.content.as[String] mustEqual Right("way:/this/works")
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(uri = "/compute/23/19")) {
          pathPrefix("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            completeWith((a.toInt + b.toInt).toString)
          }
        }.response.content.as[String] mustEqual Right("42")
      }
      "within one single path(...) combinator" in {
        test(HttpRequest(uri = "/compute/23/19")) {
          pathPrefix("compute" / "\\d+".r) { a =>
            pathPrefix("\\d+".r) { b =>
              completeWith((a.toInt + b.toInt).toString)
            }
          }
        }.response.content.as[String] mustEqual Right("42")
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        completeWith(Ok)
      } must throwA[IllegalArgumentException]
    }
  }
  
  "the predefined IntNumber PathMatcher" should {
    "properly extract digit sequences at the path end into an integer" in {
      test(HttpRequest(uri = "/id/23")) {
        path("id" / IntNumber) { echoComplete }
      }.response.content.as[String] mustEqual Right("23")
    }
    "properly extract digit sequences in the middle of the path into an integer" in {
      test(HttpRequest(uri = "/id/12345yes")) {
        path("id" / IntNumber ~ "yes") { echoComplete }
      }.response.content.as[String] mustEqual Right("12345")
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / IntNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject non-digit matches" in {
      test(HttpRequest(uri = "/id/xyz")) {
        path("id" / IntNumber) { echoComplete }
      }.handled must beFalse
    }
    "reject digit sequences representing numbers greater than Int.MaxValue" in {
      test(HttpRequest(uri = "/id/2147483648")) {
        path("id" / IntNumber) { echoComplete }
      }.handled must beFalse
    }
  }

  "the predefined JavaUUID PathMatcher" should {
    "properly extract UUID sequences at the path end into an UUID" in {
      test(HttpRequest(uri = "/id/bdea8652-f26c-40ca-8157-0b96a2a8389d")) {
        path("id" / JavaUUID) { echoComplete }
      }.response.content.as[String] mustEqual Right("bdea8652-f26c-40ca-8157-0b96a2a8389d")
    }
    "properly extract UUID sequences in the middle of the path into an UUID" in {
      test(HttpRequest(uri = "/id/bdea8652-f26c-40ca-8157-0b96a2a8389dyes")) {
        path("id" / JavaUUID ~ "yes") { echoComplete }
      }.response.content.as[String] mustEqual Right("bdea8652-f26c-40ca-8157-0b96a2a8389d")
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / JavaUUID) { echoComplete }
      }.handled must beFalse
    }
    "reject non-UUID matches" in {
      test(HttpRequest(uri = "/id/xyz")) {
        path("id" / JavaUUID) { echoComplete }
      }.handled must beFalse
    }
  }
  
  "trailing slashes in the URI" should {
    "be matched by path matchers no having a trailing slash" in {
      testService(HttpRequest(uri = "/a/")) {
        path("a") { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "be matched by path matchers having a trailing slash" in {
      testService(HttpRequest(uri = "/a/")) {
        path("a/") { completeWith(Ok) }
      }.response mustEqual Ok
    }
  }
  
  "URIs without trailing slash" should {
    "be matched by path matchers no having a trailing slash" in {
      testService(HttpRequest(uri = "/a")) {
        path("a") { completeWith(Ok) }
      }.response mustEqual Ok
    }
    "not be matched by path matchers having a trailing slash" in {
      testService(HttpRequest(uri = "/a")) {
        path("a/") { completeWith(Ok) }
      }.handled must beFalse
    }
  }

  "A PathMatcher1 constructed with the `pathMatcher` helper" should {
    val Color = pathMatcher(Map("red" -> 1, "green" -> 2, "blue" -> 3))
    "properly match its map keys" in {
      test(HttpRequest(uri = "/color/green")) {
        path("color" / Color) { echoComplete }
      }.response.content.as[String] mustEqual Right("2")
    }
    "not match something else" in {
      test(HttpRequest(uri = "/color/black")) {
        path("color" / Color) { echoComplete }
      }.handled must beFalse
    }
  }

  "the predefined PathElement PathMatcher" should {
    "properly extract chars at the path end into a String" in {
      test(HttpRequest(uri = "/id/abc")) {
        path("id" / PathElement) { echoComplete }
      }.response.content.as[String] mustEqual Right("abc")
    }
    "properly extract chars in the middle of the path into a String" in {
      test(HttpRequest(uri = "/id/yes/no")) {
        path("id" / PathElement / "no") { echoComplete }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "reject empty matches" in {
      test(HttpRequest(uri = "/id/")) {
        path("id" / PathElement) { echoComplete }
      }.handled must beFalse
    }
  }
}