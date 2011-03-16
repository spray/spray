package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpHeaders._
import MediaTypes._
import test.SprayTest
import marshalling.DefaultUnmarshallers._

class BasicBuildersSpec extends Specification with BasicBuilders with SprayTest {

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
  
  "methods(GET, POST)" should {
    "block PUT requests" in {
      test(HttpRequest(PUT)) { 
        methods(GET, POST) { notRun }
      }.handled must beFalse
    }
    "let POST requests pass" in {
      test(HttpRequest(POST)) { 
        methods(GET, POST) { completeOk }
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
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("second")    
    }
    "collect rejections from both sub routes" in {
      test(HttpRequest(DELETE)) {
        get { notRun } ~ put { notRun }
      }.rejections mustEqual Set(MethodRejection(GET), MethodRejection(PUT))
    }
  }
  
  "the cached directive" should {
    def createBuilder = new BasicBuilders {
      var i = 0
      val service = cached { _.complete { i += 1; i.toString } }
      val errorService = cached { _.complete { i += 1; HttpResponse(HttpStatus(500 + i)) } }
    }
    def createAndPrimeService = make(createBuilder) { _.service(RequestContext(HttpRequest(GET))) }
    def createAndPrimeErrorService = make(createBuilder) { _.errorService(RequestContext(HttpRequest(GET))) }
    
    "return and cache the response of the first GET" in {      
      test(HttpRequest(GET)) {
        createBuilder.service        
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response for a second GET" in {
      val builder = createAndPrimeService
      test(HttpRequest(GET)) {
        builder.service        
      }.response.content.as[String] mustEqual Right("1")
    }
    "return the cached response also for HttpFailures on GETs" in {
      val builder = createAndPrimeErrorService
      test(HttpRequest(GET)) {
        builder.service        
      }.response mustEqual failure(501)
    }
    "not cache responses for PUTs" in {
      val builder = createAndPrimeService
      test(HttpRequest(PUT)) {
        builder.service        
      }.response.content.as[String] mustEqual Right("2")
    }
  }

}