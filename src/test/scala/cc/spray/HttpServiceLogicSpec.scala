package cc.spray

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MediaTypes._
import test.SprayTest

class HttpServiceLogicSpec extends Specification with SprayTest with ServiceBuilder {
  
  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      testService(HttpRequest(GET, "/test")) {
        service {
          path("abc") { completeOk }
        }
      }.handled must beFalse
    }
    "leave only partially matched requests unhandled" in {
      "for routes on prefix paths" in {
        testService(HttpRequest(GET, "/test/more")) {
          service {
            path("test") { completeOk }
          }
        }.handled must beFalse
      }
      "for route path routes" in {
        testService(HttpRequest(GET, "/test")) {
          service {
            path("") { completeOk }
          }
        }.handled must beFalse
      }
    }
    "respond with the route response for completely matched requests" in {
      "for routes on non-root paths" in {
        testService(HttpRequest(GET, "/test")) {
          service {
            path("test") { completeOk }
          }
        }.response mustEqual Ok
      }
      "for routes on root paths" in {
        testService(HttpRequest(GET, "/")) {
          service {
            path("") { completeOk }
          }
        }.response mustEqual Ok
      }
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