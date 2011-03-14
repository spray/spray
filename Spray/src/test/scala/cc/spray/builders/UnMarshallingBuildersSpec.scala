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
      test(TestHttpService(
        service { _.fail(InternalServerError, "NOPE") }
      ), HttpRequest(GET)).response mustEqual HttpResponse(HttpStatus(InternalServerError, "NOPE"))
    }
    "return EmptyContent unchanged" in {
      test(TestHttpService(
        service { _.respond(EmptyContent) }
      ), HttpRequest(GET)).response mustEqual HttpResponse()
    }
    "convert ObjectContent to BufferContent using the default marshaller" in {
      test(TestHttpService(
        service {_.respond(<p>yes</p>) }
      ), HttpRequest(GET)).response.content.as[String] mustEqual Right("<p>yes</p>")
    }
    "convert ObjectContent to BufferContent using the in-scope marshaller" in {
      test(TestHttpService(
        service {_.respond(42) }
      ), HttpRequest(GET)).response mustEqual
              HttpResponse(content = HttpContent(ContentType(`application/xhtml+xml`), "<int>42</int>"))
    }
    "return an InternalServerError response if no marshaller is in scope" in {
      test(TestHttpService(
        service {_.respond(42.0) }
      ), HttpRequest(GET)).response mustEqual failure(InternalServerError, "No marshaller for response content '42.0'")
    }
    "return a NotAcceptable response if no acceptable marshaller is in scope" in {
      test(TestHttpService(
        service {_.respond(42) }
      ), HttpRequest(GET, headers = List(`Accept`(`text/css`)))).response mustEqual
              failure(NotAcceptable, "Resource representation is only available with these content-types:\n" +
              "ContentType(application/xhtml+xml,*)")
    }
    "let acceptable BufferContent pass" in {
      test(TestHttpService(
        service {_.respond(HttpContent(`text/css`, "CSS")) }
      ), HttpRequest(GET, headers = List(`Accept`(`text/css`)))).response mustEqual
              HttpResponse(content = HttpContent(ContentType(`text/css`), "CSS"))
    }
    "return an InternalServerError if the response BufferContent is not accepted by the client" in {
      test(TestHttpService(
        service {_.respond(HttpContent(`text/plain`, "CSS")) }
      ), HttpRequest(GET, headers = List(`Accept`(`text/css`)))).response mustEqual
              failure(InternalServerError, "Response BufferContent has unacceptable Content-Type")
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