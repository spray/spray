package cc.spray

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MimeTypes._
import test.SprayTest

class HttpServiceLogicSpec extends Specification with SprayTest {
  
  val service = new HttpServiceLogic with ServiceBuilder with ServiceTest {
    val route = {
      path("abc") {
        get { _.respond("yes") } ~
        put { _.respond("yes") }
      } ~
      path("def") {
        get { _.respond("yeah") }
      }
    } 
  }
  
  "The HttpServiceLogic" should {
    "leave requests to unmatched paths unhandled" in {
      test(service, HttpRequest(GET, "/test")).handled must beFalse
    }
    "respond with the route response for completely matched requests" in {
      "example 1" in {
        test(service, HttpRequest(GET, "/abc")).response mustEqual HttpResponse(content = "yes")
      }
      "example 2" in {
        test(service, HttpRequest(GET, "/def")).response mustEqual HttpResponse(content = "yeah")
      }
    }
    "respond with MethodNotAllowed if the request path was fully matched but the HTTP method was not handled" in {
      test(service, HttpRequest(POST, "/abc")).response mustEqual failure(MethodNotAllowed,
        "HTTP method not allowed, supported methods: GET, PUT")
    }
  }
  
}