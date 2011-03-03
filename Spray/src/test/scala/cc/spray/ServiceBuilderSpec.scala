package cc.spray

import http._
import org.specs.Specification
import MimeObjects._
import HttpMethods._
import HttpHeaders._

class ServiceBuilderSpec extends Specification with ServiceBuilder {

  "get" should {
    "block POST requests" in {
      innerHasRun { inner =>
        fire(HttpRequest(POST)) {
          get { inner }
        }
      } mustBe false
    }
    "let GET requests pass" in {
      innerHasRun { inner =>
        fire(HttpRequest(GET)) {
          get { inner }
        }
      } mustBe true
    }
  }
  
  "accepts(mimeType)" should {
    "block requests without any content" in {
      innerHasRun { inner =>
        fire(HttpRequest(GET)) {
          accepts(`text/xml`) { inner }
        }
      } mustBe false
    }
    "block requests with unmatching content" in {
      innerHasRun { inner =>
        fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/xml`) { inner }
        }
      } mustBe false
    }
    "let requests with matching content pass" in {
      "on simple one-on-one matches" in {
        innerHasRun { inner =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/html`) { inner }
          }
        } mustBe true
      }
      "as a one-of-several match " in {
        innerHasRun { inner =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/xml`, `text/html`) { inner }
          }
        } mustBe true
      }
      "as a .../* media range match" in {
        innerHasRun { inner =>
          fire(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
            accepts(`text/+`) { inner }
          }
        } mustBe true
      }
    }
  }
  
  "produces(mimeType)" should {
    "add a 'Content-Type' response header if none was present before" in {
      captureResponse { responder =>
        fire(HttpRequest(GET), responder) {
          produces(`text/plain`) { _.respond("CONTENT") }
        }
      }.headers.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
    "overwrite a previously existing 'Content-Type' response header" in {
      captureResponse { responder =>
        fire(HttpRequest(GET), responder) {
          produces(`text/plain`) { _.respond(HttpResponse(headers = List(`Content-Type`(`text/html`)))) }
        }
      }.headers.collect { case `Content-Type`(mimeType) => mimeType } mustEqual List(`text/plain`)    
    }
  }
  
  private def fire(request: HttpRequest, responder: HttpResponse => Unit = { _ => })(route: Route) {
    route(Context(request, responder))
  }
  
  private def innerHasRun(f: Route => Unit): Boolean = {
    var hasRun = false;
    f { _ => hasRun = true; true }
    hasRun
  }
  
  private def captureResponse(f: (HttpResponse => Unit) => Unit): HttpResponse = {
    var response: HttpResponse = null
    f { res => response = res }
    response
  }
}