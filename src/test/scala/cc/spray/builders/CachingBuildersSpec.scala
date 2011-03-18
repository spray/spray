package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import test.SprayTest

class CachingBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  val notRun: Route = { _ => fail("Should not run") }
  
  "the cached directive" should {
    val countingService = {
      var i = 0
      cached { _.complete { i += 1; i.toString } }
    }
    val errorService = {
      var i = 0
      cached { _.complete { i += 1; HttpResponse(HttpStatus(500 + i)) } }
    }
    def prime(route: Route) = make(route) { _(RequestContext(HttpRequest(GET))) }
    
    "return and cache the response of the first GET" in {      
      test(HttpRequest(GET)) {
        countingService
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response for a second GET" in {
      test(HttpRequest(GET)) {
        prime(countingService)        
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response also for HttpFailures on GETs" in {
      test(HttpRequest(GET)) {
        prime(errorService)        
      }.response mustEqual failure(501)
    }
    "not cache responses for PUTs" in {
      test(HttpRequest(PUT)) {
        prime(countingService)        
      }.response.content.as[String] mustEqual Right("2")
    }
  }

}