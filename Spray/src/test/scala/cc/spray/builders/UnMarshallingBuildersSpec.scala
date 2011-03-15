package cc.spray
package builders

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MimeTypes._
import test.SprayTest
import marshalling.AbstractMarshaller

class UnMarshallingBuildersSpec extends Specification with SprayTest with ServiceBuilder {
  
  "The 'service' directive" should {
    "not change failure results" in {
      testService(HttpRequest(GET)) {
        service { _.fail(InternalServerError, "NOPE") }
      }.response mustEqual HttpResponse(HttpStatus(InternalServerError, "NOPE"))
    }
    "return EmptyContent unchanged" in {
      testService(HttpRequest(GET)) {
        service { _.respond(EmptyContent) }
      }.response mustEqual HttpResponse()
    }
    "convert ObjectContent to BufferContent using the default marshaller" in {
      testService(HttpRequest(GET)) {
        service {_.respond(<p>yes</p>) }
      }.response.content.as[String] mustEqual Right("<p>yes</p>")
    }
    "convert ObjectContent to BufferContent using the in-scope marshaller" in {
      testService(HttpRequest(GET)) {
        service {_.respond(42) }
      }.response mustEqual HttpResponse(content = HttpContent(ContentType(`application/xhtml+xml`), "<int>42</int>"))
    }
    "return an InternalServerError response if no marshaller is in scope" in {
      testService(HttpRequest(GET)) {
        service {_.respond(42.0) }
      }.response mustEqual failure(InternalServerError, "No marshaller for response content '42.0'")
    }
    "return a NotAcceptable response if no acceptable marshaller is in scope" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service {_.respond(42) }
      }.response mustEqual
              failure(NotAcceptable, "Resource representation is only available with these content-types:\n" +
              "ContentType(application/xhtml+xml,*)")
    }
    "let acceptable BufferContent pass" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service {_.respond(HttpContent(`text/css`, "CSS")) }
      }.response mustEqual HttpResponse(content = HttpContent(ContentType(`text/css`), "CSS"))
    }
    "return an InternalServerError if the response BufferContent is not accepted by the client" in {
      test(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service {_.respond(HttpContent(`text/plain`, "CSS")) }
      }.response mustEqual failure(InternalServerError, "Response BufferContent has unacceptable Content-Type")
    }
  }
  
  "The 'contentAs' directive " should {
    "convert BufferContent to ObjectContent using the in-scope Unmarshaller" in {
     /* test(HttpRequest(POST)) { 
        get { respondOk }
      }
      test(TestHttpService(
        service {_.respond(<p>yes</p>) }
      ), HttpRequest(GET)).response.content.as[String] mustEqual Right("<p>yes</p>")*/
    }
    "return a BadRequest response if the request has not entity" in {
    }
    "return an UnsupportedMediaType response if no matching unmarshaller is in scope" in {
    }
  }
  
  "The 'handledBy' directive " should {
    "to be written" in {
    }
  }
  
  object IntMarshaller extends AbstractMarshaller[Int] {
    def canMarshalTo = ContentType(`application/xhtml+xml`) :: Nil
    def marshal(value: Int, contentType: ContentType) = NodeSeqMarshaller.marshal(<int>{value}</int>, contentType)
  }
  
  implicit val marshaller = IntMarshaller orElse defaultMarshaller
  
}