package cc.spray

import http._
import org.specs.Specification
import HttpMethods._
import test.SprayTest
import akka.actor.Actor
import util.Properties
import java.io.{File, FileNotFoundException}
import MimeTypes._
import HttpHeaders._

trait ServiceBuilderSpec1 {
  this: ServiceBuilderSpec =>

  "get" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        get { respondOk }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        get { respondOk }
      }.response mustEqual Ok
    }
  }
  
  "accepts(mimeType)" should {
    "block requests without any content" in {
      test(HttpRequest(GET)) {
        accepts(`text/xml`) { respondOk }
      }.handled must beFalse
    }
    "block requests with unmatching content" in {
      test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
        accepts(`text/xml`) { respondOk }
      }.handled must beFalse
    }
    "let requests with matching content pass" in {
      "on simple one-on-one matches" in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/html`) { respondOk }
        }.response mustEqual Ok
      }
      "as a one-of-several match " in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/xml`, `text/html`) {respondOk }
        }.response mustEqual Ok
      }
      "as a .../star media range match" in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/+`) { respondOk }
        }.response mustEqual Ok
      }
    }
  }
  
  "produces(mimeType)" should {
    "add a 'Content-Type' response header if none was present before" in {
      test(HttpRequest(GET)) {
        produces(`text/plain`) { respondOk }
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)))    
    }
    "overwrite a previously existing 'Content-Type' response header" in {
      test(HttpRequest(GET)) {
        produces(`text/plain`) { _.respond(HttpResponse(headers = List(`Content-Type`(`text/html`)))) }
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)))   
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { _.respond("first") } ~ get { _.respond("second") }
      }.response mustEqual HttpResponse(content = "first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { _.respond("first") } ~ get { _.respond("second") }
      }.response mustEqual HttpResponse(content = "second")    
    }
  }
  
  "routes created with the path(string) combinator" should {
    "block unmatching requests" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("hello") { respondOk }
      }.handled must beFalse
    }
    "let matching requests pass and adapt RequestContext.unmatchedPath" in {
      test(HttpRequest(GET, "/noway/this/works")) {
        path("noway") { ctx => ctx.respond(ctx.unmatchedPath) }
      }.response mustEqual HttpResponse(content = "/this/works")
    }
    "be stackable" in {
      "within one single path(...) combinator" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("noway" / "this" / "works" ~ Remaining) { remaining =>
            get { _.respond(remaining) }
          }
        }.response mustEqual HttpResponse(content = "")
      }
      "when nested" in {
        test(HttpRequest(GET, "/noway/this/works")) {
          path("noway") {
            path("this") { ctx => ctx.respond(ctx.unmatchedPath) }
          }
        }.response mustEqual HttpResponse(content = "/works")
      }
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