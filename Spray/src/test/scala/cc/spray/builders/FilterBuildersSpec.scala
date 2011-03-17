package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpStatusCodes._
import test.SprayTest

class FilterBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  val notRun: Route = { _ => fail("Should not run") }
  
  "get | put" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        (get | put) { notRun }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        (get | put) { completeOk }
      }.response mustEqual Ok
    }
    "let PUT requests pass" in {
      test(HttpRequest(PUT)) { 
        (get | put) { completeOk }
      }.response mustEqual Ok
    }
  }
  
  "host(regex) | host(otherRegex)" should {
    "block requests not matching any of the two conditions" in {
      test(HttpRequest(uri = "http://xyz.com")) { 
        (host("spray.*".r) | host("clay.*".r)) { _ => notRun }
      }.handled must beFalse
    }
    "extract the first match if it is successful" in {
      test(HttpRequest(uri = "http://www.spray.cc")) { 
        (host("[^\\.]+.spray.cc".r) | host("spray(.*)".r)) { matched => _.complete(matched) }
      }.response.content.as[String] mustEqual Right("www.spray.cc")
    }
    "extract the second match if the first failed and the second is successful" in {
      test(HttpRequest(uri = "http://spray.cc")) { 
        (host("[^\\.]+.spray.cc".r) | host("spray(.*)".r)) { matched => _.complete(matched) }
      }.response.content.as[String] mustEqual Right(".cc")
    }
  }

}