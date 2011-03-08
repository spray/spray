package cc.spray

import http._
import org.specs.Specification
import MimeObjects._
import HttpMethods._
import HttpHeaders._
import test.RouteTest

class ServiceBuilderSpec extends Specification with ServiceBuilder with RouteTest {

  val OK = ResponseContext(Some(HttpResponse()))
  val respondOk: Route = { _.respond(OK) }
  val respondWithUnmatchedPath: Route = { ctx => ctx.respond(ctx.unmatchedPath) }
  
  "get" should {
    "block POST requests" in {
      responseFor(HttpRequest(POST)) { 
        get { respondOk }
      }.response must beNone
    }
    "let GET requests pass" in {
      responseFor(HttpRequest(GET)) { 
        get { respondOk }
      } mustEqual OK
    }
  }
  
  "accepts(mimeType)" should {
    "block requests without any content" in {
      responseFor(HttpRequest(GET)) {
        accepts(`text/xml`) { respondOk }
      }.response must beNone
    }
    "block requests with unmatching content" in {
      responseFor(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
        accepts(`text/xml`) { respondOk }
      }.response must beNone
    }
    "let requests with matching content pass" in {
      "on simple one-on-one matches" in {
        responseFor(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/html`) { respondOk }
        } mustEqual OK
      }
      "as a one-of-several match " in {
        responseFor(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/xml`, `text/html`) {respondOk }
        } mustEqual OK
      }
      "as a .../star media range match" in {
        responseFor(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/+`) { respondOk }
        } mustEqual OK
      }
    }
  }
  
  "produces(mimeType)" should {
    "add a 'Content-Type' response header if none was present before" in {
      responseFor(HttpRequest(GET)) {
        produces(`text/plain`) { respondOk }
      }.responseHeaders.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
    "overwrite a previously existing 'Content-Type' response header" in {
      responseFor(HttpRequest(GET)) {
        produces(`text/plain`) { _.respond(HttpResponse(headers = List(`Content-Type`(`text/html`)))) }
      }.responseHeaders.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      responseFor(HttpRequest(GET)) {
        get { _.respond("first") } ~ get { _.respond("second") }
      }.contentAsString mustEqual "first"    
    }
    "yield the second sub route if the first did not succeed" in {
      responseFor(HttpRequest(GET)) {
        post { _.respond("first") } ~ get { _.respond("second") }
      }.contentAsString mustEqual "second"    
    }
  }
  
  "routes created with the path(string) combinator" should {
    "block unmatching requests" in {
      responseFor(HttpRequest(GET, "/noway/this/works")) {
        path("hello") { respondOk }
      }.response must beNone
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      responseFor(HttpRequest(GET, "/noway/this/works")) {
        path("noway") { respondWithUnmatchedPath }
      }.contentAsString mustEqual "/this/works"
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        responseFor(HttpRequest(GET, "/noway/this/works")) {
          path("noway" / "this" / "works") { respondWithUnmatchedPath }
        }.contentAsString mustEqual ""
      }
      "when nested" in {
        responseFor(HttpRequest(GET, "/noway/this/works")) {
          path("noway") {
            path("this") { respondWithUnmatchedPath }
          }
        }.contentAsString mustEqual "/works"
      }
    }
  }
  
  "routes created with the path(regex) combinator" should {
    "block unmatching requests" in {
      responseFor(HttpRequest(GET, "/noway/this/works")) {
        path("\\d".r) { _ => respondOk }
      }.response must beNone
    }
    "let matching requests pass, extract the match value and adapt RequestContext.unmatchedPath" in {
      "when the regex is a simple regex" in {
        responseFor(HttpRequest(GET, "/noway/this/works")) {
          path("no[^/]+".r) { capture =>
            get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
          }
        }.contentAsString mustEqual "noway:/this/works"
      }
      "when the regex is a group regex" in {
        responseFor(HttpRequest(GET, "/noway/this/works")) {
          path("no([^/]+)".r) { capture =>
            get { ctx => ctx.respond(capture + ":" + ctx.unmatchedPath) }
          }
        }.contentAsString mustEqual "way:/this/works"
      }
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        responseFor(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r / "\\d+".r) { (a, b) =>
            get { _.respond((a.toInt + b.toInt).toString) }
          }
        }.contentAsString mustEqual "42"
      }
      "within one single path(...) combinator" in {
        responseFor(HttpRequest(GET, "/compute/23/19")) {
          path("compute" / "\\d+".r) { a =>
            path("\\d+".r) { b =>
              get { _.respond((a.toInt + b.toInt).toString) }
            }
          }
        }.contentAsString mustEqual "42"
      }
    }
    "fail when the regex contains more than one group" in {
      path("compute" / "yea(\\d+)(\\d+)".r / "\\d+".r) { (a, b) =>
        get { respondOk }
      } must throwA[IllegalArgumentException]
    }
  }
  
}