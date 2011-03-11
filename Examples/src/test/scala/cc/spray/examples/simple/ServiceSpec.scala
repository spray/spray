package cc.spray
package examples.simple

import org.specs.Specification
import test.{DetachingDisabled, SprayTest}
import http._
import HttpMethods._
import HttpHeaders._
import MimeTypes._

class ServiceSpec extends Specification with SprayTest with Service with DetachingDisabled {
  
  val service = HttpServiceTest(sampleService)
  
  "The sample service" should {
    "correctly handle these example" in {
      "GET /test/echo/42" in {
        test(service, HttpRequest(GET, "/test/echo/42")).response mustEqual
                HttpResponse(content = HttpContent(`text/plain`, "The number is: 42"))
      }
      
      "GET /test/echo/Sp" in {
        test(service, HttpRequest(GET, "/test/echo/Sp")).response mustEqual
                HttpResponse(content = HttpContent(`text/plain`, "The letters are: S and p"))
      }
      
      "GET /resources/sample.html" in {
        test(service, HttpRequest(GET, "/resources/sample.html")).response mustEqual
                HttpResponse(content = HttpContent(`text/html`, "<p>Lorem ipsum!</p>"))
      }
      
      "GET /resources/notThere.txt" in {
        test(service, HttpRequest(GET, "/resources/notThere.txt")).response mustEqual
                failure(404)
      }
    }
  }
  
}