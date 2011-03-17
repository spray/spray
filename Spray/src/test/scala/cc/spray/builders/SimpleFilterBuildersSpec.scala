package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpHeaders._
import MediaTypes._
import test.SprayTest
import marshalling.DefaultUnmarshallers._

class SimpleFilterBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  val notRun: Route = { _ => fail("Should not run") }
  
  "get" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        get { notRun }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        get { completeOk }
      }.response mustEqual Ok
    }
  }
  
  "The 'host' directive" should {
    "in its simple String form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spray.com") { notRun }
        }.handled must beFalse
      }
      "let requests to matching hosts pass" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spray.cc") { completeOk }
        }.response mustEqual Ok
      }
    }
    "in its simple RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("hairspray.*".r) { _ => notRun }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spra.*".r) { host => _.complete(host) }
        }.response.content.as[String] mustEqual Right("spray.cc")
      }
    }
    "in its group RegEx form" in {
      "block requests to unmatched hosts" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("hairspray(.*)".r) { _ => notRun }
        }.handled must beFalse
      }
      "let requests to matching hosts pass and extract the full host" in {
        test(HttpRequest(uri = "http://spray.cc")) {
          host("spra(.*)".r) { host => _.complete(host) }
        }.response.content.as[String] mustEqual Right("y.cc")
      }
    }
  }

}