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
  
  "GET /resource/sampleResource.txt" in {
    test(HttpRequest(GET, "/resource/sampleResource.html")) {
      service
    }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/html`)), content = "<p>Lorem ipsum!</p>")
  }
  
}