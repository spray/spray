package cc.spray
package examples.simple

import org.specs.Specification
import test.{DetachingDisabled, SprayTest}
import http._
import HttpMethods._
import HttpHeaders._
import MimeTypes._

class ServiceSpec extends Specification with Service with SprayTest with DetachingDisabled {
  
  val service = restService
  
  "GET /test/echo/42" in {
    test(HttpRequest(GET, "/test/echo/42")) {
      service
    }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "The number is: 42")
  }
  
  "GET /test/echo/Sp" in {
    test(HttpRequest(GET, "/test/echo/Sp")) {
      service
    }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "The letters are: S and p")
  }
  
  "GET /resources/sample.html" in {
    test(HttpRequest(GET, "/resources/sample.html")) {
      service
    }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/html`)), content = "<p>Lorem ipsum!</p>")
  }
  
  "GET /resources/notThere.txt" in {
    test(HttpRequest(GET, "/resources/notThere.txt")) {
      service
    }.response mustEqual failure(404, "Resource 'samples/notThere.txt' not found")
  }
  
}