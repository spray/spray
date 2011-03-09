package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import test.SprayTest

class PathBuildersSpec extends Specification with BasicBuilders with PathBuilders with SprayTest {

  val Ok = HttpResponse()
  val respondOk: Route = { _.respond(Ok) }
  
  "routes created with the path(regex) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("\\d".r) { _ => respondOk }
      }.handled must beFalse
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("no[^/]+".r) { capture =>
            get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
          }
        }.response mustEqual HttpResponse(content = "noway:/this/works")
      }
      "when the regex is a group regex" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("no([^/]+)".r) { capture =>
            get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
          }
        }.response mustEqual HttpResponse(content = "way:/this/works")
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            get { _.respond((a.toInt + b.toInt).toString) }
          }
        }.response mustEqual HttpResponse(content = "42")
      }
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r) { a =>
            path("\\d+".r) { b =>
              get { _.respond((a.toInt + b.toInt).toString) }
            }
          }
        }.response mustEqual HttpResponse(content = "42")
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        get { respondOk }
      } must throwA[IllegalArgumentException]
    }
  }

}