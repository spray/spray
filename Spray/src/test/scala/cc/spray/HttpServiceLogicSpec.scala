package cc.spray

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MediaTypes._
import test.SprayTest

class HttpServiceLogicSpec extends Specification with SprayTest with ServiceBuilder {
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      testService(HttpRequest(GET, "/test")) {
        service {
          path("abc") { _.complete("yes") }
        }
      }.handled must beFalse
    }
    "leave only partially matched requests unhandled" in {
      testService(HttpRequest(GET, "/test/more")) {
        service {
          path("test") { _.complete("yes") }
        }
      }.handled must beFalse
    }
    "respond with the route response for completely matched requests" in {
      testService(HttpRequest(GET, "/test")) {
        service {
          path("test") { _.complete("yes") }
        }
      }.response.content.as[String] mustEqual Right("yes")
    }
    "respond with MethodNotAllowed if the request path was fully matched but the HTTP method was not handled" in {
      testService(HttpRequest(POST, "/test")) {
        service {
          path("test") {
            get { _.complete("yes") } ~
            put { _.complete("yes") }
          }
        }
      }.response mustEqual failure(MethodNotAllowed,
        "HTTP method not allowed, supported methods: GET, PUT")
    }
  }
  
}