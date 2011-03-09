package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpHeaders._
import MimeTypes._
import test.SprayTest

class BasicBuildersSpec extends Specification with BasicBuilders with SprayTest {

  val Ok = HttpResponse()
  val respondOk: Route = { _.respond(Ok) }
  
  "get" should {
    "block POST requests" in {
      test(HttpRequest(POST)) { 
        get { respondOk }
      }.handled must beFalse
    }
    "let GET requests pass" in {
      test(HttpRequest(GET)) { 
        get { respondOk }
      }.response mustEqual Ok
    }
  }
  
  "accepts(mimeType)" should {
    "block requests without any content" in {
      test(HttpRequest(GET)) {
        accepts(`text/xml`) { respondOk }
      }.handled must beFalse
    }
    "block requests with unmatching content" in {
      test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
        accepts(`text/xml`) { respondOk }
      }.handled must beFalse
    }
    "let requests with matching content pass" in {
      "on simple one-on-one matches" in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/html`) { respondOk }
        }.response mustEqual Ok
      }
      "as a one-of-several match " in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/xml`, `text/html`) {respondOk }
        }.response mustEqual Ok
      }
      "as a .../star media range match" in {
        test(HttpRequest(GET, headers = List(`Content-Type`(`text/html`)))) {
          accepts(`text/+`) { respondOk }
        }.response mustEqual Ok
      }
    }
  }
  
  "produces(mimeType)" should {
    "add a 'Content-Type' response header if none was present before" in {
      test(HttpRequest(GET)) {
        produces(`text/plain`) { respondOk }
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)))    
    }
    "overwrite a previously existing 'Content-Type' response header" in {
      test(HttpRequest(GET)) {
        produces(`text/plain`) { _.respond(HttpResponse(headers = List(`Content-Type`(`text/html`)))) }
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)))   
    }
  }
  
  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { _.respond("first") } ~ get { _.respond("second") }
      }.response mustEqual HttpResponse(content = "first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { _.respond("first") } ~ get { _.respond("second") }
      }.response mustEqual HttpResponse(content = "second")    
    }
  }
  
  "the cached directive" should {
    def createBuilder = new BasicBuilders {
      var i = 0
      val service = cached {
        produces(`text/plain`) { _.respond { i += 1; i.toString } }
      }
    }
    def createAndPrimeBuilder = make(createBuilder) { _.service(RequestContext(HttpRequest(GET))) }
    
    "return and cache the response of the first GET" in {      
      test(HttpRequest(GET)) {
        createBuilder.service        
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "1")
    }
    "return the cached response for a GET if the client accepts the cached responses MimeType" in {
      val builder = createAndPrimeBuilder
      test(HttpRequest(GET, headers = List(Accept(`text/+`)))) {
        builder.service        
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "1")
    }
    "return the cached response for a GET if the request carries no Accept header" in {
      val builder = createAndPrimeBuilder
      test(HttpRequest(GET)) {
        builder.service        
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "1")
    }
    "not return the cached response if the client does not accept the cached responses MimeType" in {
      val builder = createAndPrimeBuilder
      test(HttpRequest(GET, headers = List(Accept(`application/+`)))) {
        builder.service        
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "2")
    }
    "not cache responses for PUTs" in {
      val builder = createAndPrimeBuilder
      test(HttpRequest(PUT)) {
        builder.service        
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/plain`)), content = "2")
    }
  }

}