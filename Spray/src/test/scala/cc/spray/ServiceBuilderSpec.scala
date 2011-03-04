package cc.spray

import http._
import org.specs.Specification
import MimeObjects._
import HttpMethods._
import HttpHeaders._

class ServiceBuilderSpec extends Specification with ServiceBuilder {

  "get" should {
    "block POST requests" in {
      captureContext { capture =>
        fire(HttpRequest(POST)) {
          get { capture }
        }
      } must beNone
    }
    "let GET requests pass" in {
      captureContext { capture =>
        fire(HttpRequest(GET)) {
          get { capture }
        }
      } must beSomething
    }
  }
  
  "accepts(mimeType)" should {
    "block requests without any content" in {
      captureContext { capture =>
        fire(HttpRequest(GET)) {
          accepts(`text/xml`) { capture }
        }
      } must beNone
    }
    "block requests with unmatching content" in {
      captureContext { capture =>
        fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/xml`) { capture }
        }
      } must beNone
    }
    "let requests with matching content pass" in {
      "on simple one-on-one matches" in {
        captureContext { capture =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/html`) { capture }
          }
        } must beSomething
      }
      "as a one-of-several match " in {
        captureContext { capture =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/xml`, `text/html`) { capture }
          }
        } must beSomething
      }
      "as a .../* media range match" in {
        captureContext { capture =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/+`) { capture }
          }
        } must beSomething
      }
    }
  }
  
  "produces(mimeType)" should {
    "add a 'Content-Type' response header if none was present before" in {
      captureResponse {
        fire(HttpRequest(GET), _) {
          produces(`text/plain`) { _.respond("CONTENT") }
        }
      }.headers.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
    "overwrite a previously existing 'Content-Type' response header" in {
      captureResponse {
        fire(HttpRequest(GET), _) {
          produces(`text/plain`) { _.respond(HttpResponse(headers = List(`Content-Type`(`text/html`)))) }
        }
      }.headers.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      captureResponseString {
        fire(HttpRequest(GET), _) {
          get { _.respond("first") } ~ get { _.respond("second") }
        }
      } mustEqual "first"    
    }
    "yield the second sub route if the first did not succeed" in {
      captureResponseString {
        fire(HttpRequest(GET), _) {
          post { _.respond("first") } ~ get { _.respond("second") }
        }
      } mustEqual "second"    
    }
  }
  
  "routes created with the path(string) combinator" should {
    "block unmatching requests" in {
      captureContext { capture =>
        fire(HttpRequest(GET, "/noway/this/works")) {
          path("hello") { capture }
        }
      } must beNone
    }
    "let matching requests pass and adapt Context.unmatchedPath" in {
      captureContext { capture =>
        fire(HttpRequest(GET, "/noway/this/works")) {
          path("noway") { capture }
        }
      }.map(_.unmatchedPath) must beSome("/this/works")
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        captureContext { capture =>
          fire(HttpRequest(GET, "/noway/this/works")) {
            path("noway" / "this" / "works") { capture }
          }
        }.map(_.unmatchedPath) must beSome("")
      }
      "when nested" in {
        captureContext { capture =>
          fire(HttpRequest(GET, "/noway/this/works")) {
            path("noway") {
              path("this") {
                capture
              }
            }
          }
        }.map(_.unmatchedPath) must beSome("/works")
      }
    }
  }
  
  "routes created with the path(regex) combinator" should {
    "block unmatching requests" in {
      captureContext { capture =>
        fire(HttpRequest(GET, "/noway/this/works")) {
          path("\\d".r) { _ => capture }
        }
      } must beNone
    }
    "let matching requests pass, extract the match value and adapt Context.unmatchedPath" in {
      "when the regex is a simple regex" in {
        captureResponseString {
          fire(HttpRequest(GET, "/noway/this/works"), _) {
            path("no[^/]+".r) { capture =>
              get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
            }
          }
        } mustEqual "noway:/this/works"
      }
      "when the regex is a group regex" in {
        captureResponseString {
          fire(HttpRequest(GET, "/noway/this/works"), _) {
            path("no([^/]+)".r) { capture =>
              get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
            }
          }
        } mustEqual "way:/this/works"
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        captureResponseString {
          fire(HttpRequest(GET, "/compute/23/19"), _) {
            path("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
              get { _.respond((a.toInt + b.toInt).toString) }
            }
          }
        } mustEqual "42"
      }
      "within one single path(...) combinator" in {
        captureResponseString {
          fire(HttpRequest(GET, "/compute/23/19"), _) {
            path("compute" / "\\d+".r) { a =>
              path("\\d+".r) { b =>
                get { _.respond((a.toInt + b.toInt).toString) }
              }
            }
          }
        } mustEqual "42"
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        get { _ => true }
      } must throwA[IllegalArgumentException]
    }
  }
  
  private def fire(request: HttpRequest, responder: HttpResponse => Unit = { _ => })(route: Route) {
    route(Context(request, responder))
  }
  
  private def captureContext(f: Route => Unit): Option[Context] = {
    var context: Option[Context] = None;
    f { ctx => context = Some(ctx); true }
    context
  }
  
  private def captureResponse(f: (HttpResponse => Unit) => Unit): HttpResponse = {
    var response: Option[HttpResponse] = None
    f { res => response = Some(res) }
    response.getOrElse(fail("No response received"))
  }
  
  private def captureResponseString(f: (HttpResponse => Unit) => Unit): String = {
    captureResponse(f).content.map(new String(_)).getOrElse(fail("Response has no content"))
  }
}