/*
 * Copyright (C) 2011-2013 spray.io
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

  val echoUnmatchedPath = unmatchedPath { echoComplete }
  val echoCaptureAndUnmatchedPath: String ⇒ Route =
    capture ⇒ ctx ⇒ ctx.complete(capture + ":" + ctx.unmatchedPath)

  "routes created with the path(string) combinator" should {
    "block completely unmatching requests" in {
      Get("/noway/this/works") ~> path("hello") { completeOk } ~> check { handled must beFalse }
    }
    "block prefix requests" in {
      Get("/noway/this/works") ~> path("noway" / "this") { completeOk } ~> check { handled must beFalse }
    }
    "let fully matching requests pass and clear the RequestContext.unmatchedPath" in {
      Get("/noway/this/works") ~> {
        path("noway" / "this" / "works") { echoUnmatchedPath }
      } ~> check { entityAs[String] === "" }
    }
    "implicitly match trailing slashes" in {
      Get("/works/") ~> path("works") { completeOk } ~> check { response === Ok }
      Get("/") ~> path("") { completeOk } ~> check { response === Ok }
    }
  }

  "routes created with the pathPrefix(string) combinator" should {
    "block unmatching requests" in {
      Get("/noway/this/works") ~> pathPrefix("hello") { completeOk } ~> check { handled must beFalse }
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      Get("/noway/this/works") ~> {
        pathPrefix("noway") { echoUnmatchedPath }
      } ~> check { entityAs[String] === "/this/works" }
    }
    "match and consume segment prefixes" in {
      Get("/abc/efg") ~> {
        pathPrefix("abc" / "e") { echoUnmatchedPath }
      } ~> check { entityAs[String] === "fg" }
    }
    "be stackable" in {
      "within one single pathPrefix(...) combinator" in {
        Get("/noway/this/works") ~> {
          pathPrefix("noway" / "this" / "works" ~ Rest) { echoComplete }
        } ~> check { entityAs[String] === "" }
      }
      "when nested" in {
        Get("/noway/this/works") ~> {
          (pathPrefix("noway") & pathPrefix("this")) { echoUnmatchedPath }
        } ~> check { entityAs[String] === "/works" }
      }
    }
  }

  "routes created with the pathPrefix(regex) combinator" should {
    "block unmatching requests" in {
      Get("/noway/this/works") ~> pathPrefix("\\d".r) { echoComplete } ~> check { handled must beFalse }
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex (example 1)" in {
        Get("/abcdef/ghijk/lmno") ~> {
          pathPrefix("abcdef" / "ghijk".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "ghijk:/lmno" }
      }
      "when the regex is a simple regex (example 2)" in {
        Get("/abcdef/ghijk/lmno") ~> {
          pathPrefix("abcdef" / "gh[ij]+".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "ghij:k/lmno" }
      }
      "when the regex is a group regex (example 1)" in {
        Get("/abcdef/ghijk/lmno") ~> {
          pathPrefix("abcdef" / "(ghijk)".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "ghijk:/lmno" }
      }
      "when the regex is a group regex (example 2)" in {
        Get("/abcdef/ghijk/lmno") ~> {
          pathPrefix("abcdef" / "gh(ij)".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "ij:k/lmno" }
      }
      "when the regex is a group regex (example 3)" in {
        Get("/abcdef/ghijk/lmno") ~> {
          pathPrefix("abcdef" / "(gh)i".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "gh:jk/lmno" }
      }
      "for segment prefixes" in {
        Get("/noway/this/works") ~> {
          pathPrefix("n([ow]+)".r) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "ow:ay/this/works" }
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        Get("/compute/23/19") ~> {
          pathPrefix("compute" / "\\d+".r / "\\d+".r) { (a, b) ⇒
            complete((a.toInt + b.toInt).toString)
          }
        } ~> check { entityAs[String] === "42" }
      }
      "within one single path(...) combinator" in {
        Get("/compute/23/19") ~> {
          pathPrefix("compute" / "\\d+".r) { a ⇒
            pathPrefix("\\d+".r) { b ⇒
              complete((a.toInt + b.toInt).toString)
            }
          }
        } ~> check { entityAs[String] === "42" }
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) ⇒
        completeOk
      } must throwA[IllegalArgumentException]
    }
  }

  "the predefined IntNumber PathMatcher" should {
    val route = path("id" / IntNumber) { echoComplete }
    "properly extract digit sequences at the path end into an integer" in {
      Get("/id/23") ~> route ~> check { entityAs[String] === "23" }
    }
    "properly extract digit sequences in the middle of the path into an integer" in {
      Get("/id/12345yes") ~> {
        path("id" / IntNumber ~ "yes") { echoComplete }
      } ~> check { entityAs[String] === "12345" }
    }
    "reject empty matches" in {
      Get("/id/") ~> route ~> check { handled must beFalse }
    }
    "reject non-digit matches" in {
      Get("/id/xyz") ~> route ~> check { handled must beFalse }
    }
    "reject digit sequences representing numbers greater than Int.MaxValue" in {
      Get("/id/2147483648") ~> route ~> check { handled must beFalse }
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
      Get("/id/") ~> path("id" / JavaUUID) { echoComplete } ~> check { handled must beFalse }
    }
    "reject non-UUID matches" in {
      Get("/id/xyz") ~> path("id" / JavaUUID) { echoComplete } ~> check { handled must beFalse }
    }
  }

  "trailing slashes in the URI" should {
    "be matched by path matchers no having a trailing slash" in {
      Get("/a/") ~> path("a") { completeOk } ~> check { response === Ok }
    }
    "be matched by path matchers having a trailing slash" in {
      Get("/a/") ~> path("a" ~ Slash) { completeOk } ~> check { response === Ok }
    }
  }

  "URIs without trailing slash" should {
    "be matched by path matchers no having a trailing slash" in {
      Get("/a") ~> path("a") { completeOk } ~> check { response === Ok }
    }
    "not be matched by path matchers having a trailing slash" in {
      Get("/a") ~> path("a/") { completeOk } ~> check { handled must beFalse }
    }
  }

  "A PathMatcher constructed implicitly from a Map[String, T]" should {
    val colors = Map("red" -> 1, "green" -> 2, "blue" -> 3)
    val route = path("color" / colors) { echoComplete }
    "properly match its map keys" in {
      Get("/color/green") ~> route ~> check { entityAs[String] === "2" }
    }
    "not match something else" in {
      Get("/color/black") ~> route ~> check { handled must beFalse }
    }
  }

  "The predefined Segment PathMatcher" should {
    val route = path("id" / Segment) { echoComplete }
    "properly extract chars at the path end into a String" in {
      Get("/id/abc") ~> route ~> check { entityAs[String] === "abc" }
    }
    "properly extract chars in the middle of the path into a String" in {
      Get("/id/yes/no") ~> path("id" / Segment / "no") { echoComplete } ~> check { entityAs[String] === "yes" }
    }
    "reject empty matches" in {
      Get("/id/") ~> route ~> check { handled must beFalse }
    }
  }

  "The `separateOnSlashes` creator" should {
    "properly match several path segments" in {
      Get("/a/b") ~> path(separateOnSlashes("a/b")) { completeOk } ~> check { response === Ok }
      Get("/a/b") ~> path(separateOnSlashes("a/b/")) { completeOk } ~> check { response === Ok }
    }
    "properly match several a single segment" in {
      Get("/abc") ~> path(separateOnSlashes("abc")) { completeOk } ~> check { response === Ok }
    }
    "not match on mismatching path segments" in {
      Get("/abc/def/ghi") ~> path(separateOnSlashes("abc/def/ghj")) { completeOk } ~> check { handled must beFalse }
      Get("/abc/def/ghi") ~> path(separateOnSlashes("/abc/def/ghi")) { completeOk } ~> check { handled must beFalse }
    }
  }

  "The `pathPrefixTest` directive" should {
    "match uris without consuming them" in {
      Get("/a") ~> pathPrefixTest("a") { echoUnmatchedPath } ~> check { entityAs[String] === "/a" }
    }
    "be usable for testing for trailing slashs in URIs" in {
      val route = pathPrefixTest(Segment ~ Slash_!) { _ ⇒ completeOk }
      "example 1" in {
        Get("/a/") ~> route ~> check { response === Ok }
      }
      "example 2" in {
        Get("/a") ~> route ~> check { handled === false }
      }
    }
  }

  "The `pathSuffix` directive" should {
    "allow matching and consuming of path suffixes" in {
      "example 1" in {
        Get("/orders/123/edit") ~> {
          pathSuffix("edit" / Segment) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "123:/orders/" }
      }
      "example 2" in {
        Get("/orders/123/edit/") ~> {
          pathSuffix(Slash ~ "edit" / Segment) { echoCaptureAndUnmatchedPath }
        } ~> check { entityAs[String] === "123:/orders/" }
      }
      "example 3" in {
        Get("/orders/123/edit") ~> {
          pathSuffix("edit" / IntNumber / "orders" ~ Slash ~ PathEnd) { echoComplete }
        } ~> check { entityAs[String] === "123" }
      }
    }
  }

  "The `pathSuffixTest` matcher modifier" should {
    "enable testing for trailing slashes" in {
      val route = pathSuffixTest(Slash) { completeOk }
      "example 1" in {
        Get("/a/b/") ~> route ~> check { response === Ok }
      }
      "example 2" in {
        Get("/a/b") ~> route ~> check { handled must beFalse }
      }
    }
  }

  "PathMatchers" should {
    "support the map modifier" in {
      import shapeless._
      Get("/yes-no") ~> {
        path(Rest.map { case s :: HNil ⇒ s.split('-').toList :: HNil }) { echoComplete }
      } ~> check { entityAs[String] === "List(yes, no)" }
    }
    "support the `|` operator" in {
      val route = path("ab" / ("cd" | "ef") / "gh") { completeOk }
      "example 1" in {
        Get("/ab/cd/gh") ~> route ~> check { response === Ok }
      }
      "example 2" in {
        Get("/ab/ef/gh") ~> route ~> check { response === Ok }
      }
      "example 3" in {
        Get("/ab/xy/gh") ~> route ~> check { handled === false }
      }
      "example 4" in {
        val route = path(LongNumber | JavaUUID) { echoComplete }
        Get("/123") ~> route ~> check { entityAs[String] === "123" }
        Get("/bdea8652-f26c-40ca-8157-0b96a2a8389d") ~> route ~> check { entityAs[String] must startWith("bdea8652") }
      }

    }
    "support the unary_! modifier" in {
      val route = pathPrefix("ab" / !"cd" ~ Rest) { echoComplete }
      "example 1a" in {
        Get("/ab/cef") ~> route ~> check { entityAs[String] === "cef" }
      }
      "example 2" in {
        Get("/ab/cde") ~> route ~> check { handled === false }
      }
    }
  }
}