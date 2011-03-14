package cc.spray

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MimeTypes._
import test.SprayTest

class HttpServiceLogicSpec extends Specification with SprayTest with ServiceBuilder {
  
  val route = service {
    path("abc") {
        get { _.respond("yes") } ~
        put { _.respond("yes") }
      } ~
      path("def") {
        get { _.respond("yeah") }
      }
  }
  
  val testService = TestHttpService(route)
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      test(testService, HttpRequest(GET, "/test")).handled must beFalse
    }
    "respond with the route response for completely matched requests" in {
      "example 1" in {
        test(testService, HttpRequest(GET, "/abc")).response.content.as[String] mustEqual Right("yes")
      }
      "example 2" in {
        test(testService, HttpRequest(GET, "/def")).response.content.as[String] mustEqual Right("yeah")
      }
    }
    "respond with MethodNotAllowed if the request path was fully matched but the HTTP method was not handled" in {
      test(testService, HttpRequest(POST, "/abc")).response mustEqual failure(MethodNotAllowed,
        "HTTP method not allowed, supported methods: GET, PUT")
    }
  }
  
}