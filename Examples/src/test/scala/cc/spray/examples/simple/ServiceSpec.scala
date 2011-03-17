package cc.spray
package examples.simple

import org.specs.Specification
import test.{ServiceBuilderNoDetach, SprayTest}
import http._
import HttpMethods._
import HttpHeaders._
import MediaTypes._

class ServiceSpec extends Specification with SprayTest with Service with ServiceBuilderNoDetach {
  
  "The sample service" should {
    "correctly handle these example" in {
      "GET /test/echo/42" in {
        testService(HttpRequest(GET, "/test/echo/42")) {
          sampleService
        }.response mustEqual HttpResponse(content = HttpContent(`text/plain`, "The number is: 42"))
      }
      
      "GET /test/echo/Sp" in {
        testService(HttpRequest(GET, "/test/echo/Sp")) {
          sampleService
        }. response mustEqual HttpResponse(content = HttpContent(`text/plain`, "The letters are: S and p"))
      }
      
      "GET /resources/sample.html" in {
        testService(HttpRequest(GET, "/resources/sample.html")) {
          sampleService
        }.response mustEqual HttpResponse(content = HttpContent(`text/html`, "<p>Lorem ipsum!</p>"))
      }
      
      "GET /resources/notThere.txt" in {
        testService(HttpRequest(GET, "/resources/notThere.txt")) {
          sampleService
        }.response mustEqual failure(404)
      }
    }
  }
  
}