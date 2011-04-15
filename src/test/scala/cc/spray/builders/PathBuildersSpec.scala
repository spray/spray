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
import test.SprayTest

class PathBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }

  "routes created with the path(string) combinator" should {
    "block completely unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("hello") { completeOk }
      }.handled must beFalse
    }
    "block prefix requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("noway/this") { completeOk }
      }.handled must beFalse
    }
    "let fully matching requests pass and clear the RequestContext.unmatchedPath" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("noway/this/works") { ctx => ctx.complete(HttpResponse(content = Some(HttpContent(ctx.unmatchedPath)))) }
      }.response.content.as[String] mustEqual Right("")
    }
    "be stackable within one single path(...) combinator" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("noway" / "this" / "works") {
          ctx => ctx.complete(HttpResponse(content = Some(HttpContent(ctx.unmatchedPath))))
        }
      }.response.content.as[String] mustEqual Right("")
    }
    "implicitly match trailing slashes" in {
      test(HttpRequest(GET, "/works/")) {
        path("works") { completeOk }
      }.response mustBe Ok
      test(HttpRequest(GET, "")) {
        path("") { completeOk }
      }.response mustBe Ok
    }
  }
  
  "routes created with the pathPrefix(string) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        pathPrefix("hello") { completeOk }
      }.handled must beFalse
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        pathPrefix("noway") { ctx => ctx.complete(HttpResponse(content = Some(HttpContent(ctx.unmatchedPath)))) }
      }.response.content.as[String] mustEqual Right("/this/works")
    }
    "be stackable" in {
      "within one single pathPrefix(...) combinator" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          pathPrefix("noway" / "this" / "works" ~ Remaining) { remaining =>
            _.complete(HttpResponse(content = Some(HttpContent(remaining))))
          }
        }.response.content.as[String] mustEqual Right("")
      }
      "when nested" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          pathPrefix("noway") {
            pathPrefix("this") { ctx => ctx.complete(HttpResponse(content = Some(HttpContent(ctx.unmatchedPath)))) }
          }
        }.response.content.as[String] mustEqual Right("/works")
      }
    }
  }
  
  "routes created with the pathPrefix(regex) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        pathPrefix("\\d".r) { _ => completeOk }
      }.handled must beFalse
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          pathPrefix("no[^/]+".r) { capture =>
            ctx => ctx.responder(Respond(HttpResponse(content = Some(HttpContent(capture + ":" + ctx.unmatchedPath)))))
          }
        }.response.content.as[String] mustEqual Right("noway:/this/works")
      }
      "when the regex is a group regex" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          pathPrefix("no([^/]+)".r) { capture =>
            ctx => ctx.responder(Respond(HttpResponse(content = Some(HttpContent(capture + ":" + ctx.unmatchedPath)))))
          }
        }.response.content.as[String] mustEqual Right("way:/this/works")
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          pathPrefix("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            _.complete((a.toInt + b.toInt).toString)
          }
        }.response.content.as[String] mustEqual Right("42")
      }
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          pathPrefix("compute" / "\\d+".r) { a =>
            pathPrefix("\\d+".r) { b =>
              _.complete((a.toInt + b.toInt).toString)
            }
          }
        }.response.content.as[String] mustEqual Right("42")
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        completeOk
      } must throwA[IllegalArgumentException]
    }
  }
  
  "the predefined IntNumber PathMatcher" should {
    "properly extract digit sequences at the path end into an integer" in {
      test(HttpRequest(GET, "/id/23")) {
        path("id" / IntNumber) { i =>
          _.complete(i.toString)
        }
      }.response.content.as[String] mustEqual Right("23")
    }
    "properly extract digit sequences in the middle of the path into an integer" in {
      test(HttpRequest(GET, "/id/12345yes")) {
        path("id" / IntNumber ~ "yes") { i =>
          _.complete(i.toString)
        }
      }.response.content.as[String] mustEqual Right("12345")
    }
    "reject empty matches" in {
      test(HttpRequest(GET, "/id/")) {
        path("id" / IntNumber) { i =>
          _.complete(i.toString)
        }
      }.handled must beFalse
    }
    "reject non-digit matches" in {
      test(HttpRequest(GET, "/id/xyz")) {
        path("id" / IntNumber) { i =>
          _.complete(i.toString)
        }
      }.handled must beFalse
    }
    "reject digit sequences representing numbers greater than Int.MaxValue" in {
      test(HttpRequest(GET, "/id/2147483648")) {
        path("id" / IntNumber) { i =>
          _.complete(i.toString)
        }
      }.handled must beFalse
    }
  }
  
  "trailing slashes in the URI" should {
    "be matched by path matchers no having a trailing slash" in {
      testService(HttpRequest(GET, "/a/")) {
        path("a") { completeOk }
      }.response mustBe Ok
    }
    "be matched by path matchers having a trailing slash" in {
      testService(HttpRequest(GET, "/a/")) {
        path("a/") { completeOk } 
      }.response mustBe Ok
    }
  }
  
  "URIs without trailing slash" should {
    "be matched by path matchers no having a trailing slash" in {
      testService(HttpRequest(GET, "/a")) {
        path("a") { completeOk }
      }.response mustBe Ok
    }
    "not be matched by path matchers having a trailing slash" in {
      testService(HttpRequest(GET, "/a")) {
        path("a/") { completeOk } 
      }.handled must beFalse
    }
  }

}