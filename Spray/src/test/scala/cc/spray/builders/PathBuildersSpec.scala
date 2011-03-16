package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import test.SprayTest
import marshalling.DefaultUnmarshallers._

class PathBuildersSpec extends Specification with BasicBuilders with PathBuilders with SprayTest {

  val Ok = HttpResponse()
  val respondOk: Route = { _.responder(Right(Ok)) } // don't use "complete" -> don't get any unmatched path rejections
  
  "routes created with the path(string) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("hello") { respondOk }
      }.handled must beFalse
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("noway") { ctx => ctx.responder(Right(HttpResponse(content = ObjectContent(ctx.unmatchedPath)))) }
      }.response.content.as[String] mustEqual Right("/this/works")
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("noway" / "this" / "works" ~ Remaining) { remaining =>
            get { _.responder(Right(HttpResponse(content = ObjectContent(remaining)))) }
          }
        }.response.content.as[String] mustEqual Right("")
      }
      "when nested" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("noway") {
            path("this") { ctx => ctx.responder(Right(HttpResponse(content = ObjectContent(ctx.unmatchedPath)))) }
          }
        }.response.content.as[String] mustEqual Right("/works")
      }
    }
    "add a PathMatchedRejection in case the request was rejected" in {
      test(HttpRequest(POST, "/test")) {
        path("test") {
          get { respondOk }
        }
      }.rejections mustEqual Set(MethodRejection(GET), PathMatchedRejection)
    }
  }
  
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
            get { ctx => ctx.responder(Right(HttpResponse(content = ObjectContent(capture + ":" + ctx.unmatchedPath)))) }
          }
        }.response.content.as[String] mustEqual Right("noway:/this/works")
      }
      "when the regex is a group regex" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("no([^/]+)".r) { capture =>
            get { ctx => ctx.responder(Right(HttpResponse(content = ObjectContent(capture + ":" + ctx.unmatchedPath)))) }
          }
        }.response.content.as[String] mustEqual Right("way:/this/works")
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            get { _.complete((a.toInt + b.toInt).toString) }
          }
        }.response.content.as[String] mustEqual Right("42")
      }
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r) { a =>
            path("\\d+".r) { b =>
              get { _.complete((a.toInt + b.toInt).toString) }
            }
          }
        }.response.content.as[String] mustEqual Right("42")
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        get { respondOk }
      } must throwA[IllegalArgumentException]
    }
  }

}