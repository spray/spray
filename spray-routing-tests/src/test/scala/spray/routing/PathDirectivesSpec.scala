/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing


class PathDirectivesSpec extends RoutingSpec {

  "routes created with the path(string) combinator" should {
    "block completely unmatching requests" in {
      Get("/noway/this/works") ~> {
        path("hello") { completeOk }
      } ~> check { handled must beFalse }
    }
    "block prefix requests" in {
      Get("/noway/this/works") ~> {
        path("noway/this") { completeOk }
      } ~> check { handled must beFalse }
    }
    "let fully matching requests pass and clear the RequestContext.unmatchedPath" in {
      Get("/noway/this/works") ~> {
        path("noway/this/works") { ctx => ctx.complete(ctx.unmatchedPath) }
      } ~> check { entityAs[String] === "" }
    }
    "be stackable within one single path(...) combinator" in {
      Get("/noway/this/works") ~> {
        path("noway" / "this" / "works") {
          ctx => ctx.complete(ctx.unmatchedPath)
        }
      } ~> check { entityAs[String] == "" }
    }
    "implicitly match trailing slashes" in {
      Get("/works/") ~> {
        path("works") { completeOk }
      } ~> check { response === Ok }
      Get("") ~> {
        path("") { completeOk }
      } ~> check { response === Ok }
    }
  }
  
  "routes created with the pathPrefix(string) combinator" should {
    "block unmatching requests" in {
      Get("/noway/this/works") ~> {
        pathPrefix("hello") { completeOk }
      } ~> check { handled must beFalse }
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      Get("/noway/this/works") ~> {
        pathPrefix("noway") { ctx => ctx.complete(ctx.unmatchedPath) }
      } ~> check { entityAs[String] === "/this/works" }
    }
    "be stackable" in {
      "within one single pathPrefix(...) combinator" in {
        Get("/noway/this/works") ~> {
          pathPrefix("noway" / "this" / "works" ~ Rest) {
            echoComplete
          }
        } ~> check { entityAs[String] === "" }
      }
      "when nested" in {
        Get("/noway/this/works") ~> {
          pathPrefix("noway") {
            pathPrefix("this") { ctx => ctx.complete(ctx.unmatchedPath) }
          }
        } ~> check { entityAs[String] === "/works" }
      }
    }
  }
//
  "routes created with the pathPrefix(regex) combinator" should {
    "block unmatching requests" in {
      Get("/noway/this/works") ~> {
        pathPrefix("\\d".r) { echoComplete }
      } ~> check { handled must beFalse }
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex" in {
        Get("/noway/this/works") ~> {
          pathPrefix("no[^/]+".r) { capture =>
            ctx => ctx.complete(capture + ":" + ctx.unmatchedPath)
          }
        } ~> check { entityAs[String] === "noway:/this/works" }
      }
      "when the regex is a group regex" in {
        Get("/noway/this/works") ~> {
          pathPrefix("no([^/]+)".r) { capture =>
            ctx => ctx.complete(capture + ":" + ctx.unmatchedPath)
          }
        } ~> check { entityAs[String] === "way:/this/works" }
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        Get("/compute/23/19") ~> {
          pathPrefix("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            complete((a.toInt + b.toInt).toString)
          }
        } ~> check { entityAs[String] === "42" }
      }
      "within one single path(...) combinator" in {
        Get("/compute/23/19") ~> {
          pathPrefix("compute" / "\\d+".r) { a =>
            pathPrefix("\\d+".r) { b =>
              complete((a.toInt + b.toInt).toString)
            }
          }
        } ~> check { entityAs[String] === "42" }
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
      Get("/id/23") ~> {
        path("id" / IntNumber) { echoComplete }
      } ~> check { entityAs[String] === "23" }
    }
    "properly extract digit sequences in the middle of the path into an integer" in {
      Get("/id/12345yes") ~> {
        path("id" / IntNumber ~ "yes") { echoComplete }
      } ~> check { entityAs[String] === "12345" }
    }
    "reject empty matches" in {
      Get("/id/") ~> {
        path("id" / IntNumber) { echoComplete }
      } ~> check { handled must beFalse }
    }
    "reject non-digit matches" in {
      Get("/id/xyz") ~> {
        path("id" / IntNumber) { echoComplete }
      } ~> check { handled must beFalse }
    }
    "reject digit sequences representing numbers greater than Int.MaxValue" in {
      Get("/id/2147483648") ~> {
        path("id" / IntNumber) { echoComplete }
      } ~> check { handled must beFalse }
    }
  }

  "the predefined JavaUUID PathMatcher" should {
    "properly extract UUID sequences at the path end into an UUID" in {
      Get("/id/bdea8652-f26c-40ca-8157-0b96a2a8389d") ~> {
        path("id" / JavaUUID) { echoComplete }
      } ~> check { entityAs[String] === "bdea8652-f26c-40ca-8157-0b96a2a8389d" }
    }
    "properly extract UUID sequences in the middle of the path into an UUID" in {
      Get("/id/bdea8652-f26c-40ca-8157-0b96a2a8389dyes") ~> {
        path("id" / JavaUUID ~ "yes") { echoComplete }
      } ~> check { entityAs[String] === "bdea8652-f26c-40ca-8157-0b96a2a8389d" }
    }
    "reject empty matches" in {
      Get("/id/") ~> {
        path("id" / JavaUUID) { echoComplete }
      } ~> check { handled must beFalse }
    }
    "reject non-UUID matches" in {
      Get("/id/xyz") ~> {
        path("id" / JavaUUID) { echoComplete }
      } ~> check { handled must beFalse }
    }
  }

  "trailing slashes in the URI" should {
    "be matched by path matchers no having a trailing slash" in {
      Get("/a/") ~> {
        path("a") { completeOk }
      } ~> check { response === Ok }
    }
    "be matched by path matchers having a trailing slash" in {
      Get("/a/") ~> {
        path("a/") { completeOk }
      } ~> check { response === Ok }
    }
  }

  "URIs without trailing slash" should {
    "be matched by path matchers no having a trailing slash" in {
      Get("/a") ~> {
        path("a") { completeOk }
      } ~> check { response === Ok }
    }
    "not be matched by path matchers having a trailing slash" in {
      Get("/a") ~> {
        path("a/") { completeOk }
      } ~> check { handled must beFalse }
    }
  }

  "A PathMatcher constructed implicitly from a Map[String, T]" should {
    val colors = Map("red" -> 1, "green" -> 2, "blue" -> 3)
    "properly match its map keys" in {
      Get("/color/green") ~> {
        path("color" / colors) { echoComplete }
      } ~> check { entityAs[String] === "2" }
    }
    "not match something else" in {
      Get("/color/black") ~> {
        path("color" / colors) { echoComplete }
      } ~> check { handled must beFalse }
    }
  }

  "the predefined PathElement PathMatcher" should {
    "properly extract chars at the path end into a String" in {
      Get("/id/abc") ~> {
        path("id" / PathElement) { echoComplete }
      } ~> check { entityAs[String] === "abc" }
    }
    "properly extract chars in the middle of the path into a String" in {
      Get("/id/yes/no") ~> {
        path("id" / PathElement / "no") { echoComplete }
      } ~> check { entityAs[String] === "yes" }
    }
    "reject empty matches" in {
      Get("/id/") ~> {
        path("id" / PathElement) { echoComplete }
      } ~> check { handled must beFalse }
    }
  }
}