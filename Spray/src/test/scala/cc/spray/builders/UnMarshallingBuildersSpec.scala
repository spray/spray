package cc.spray
package builders

import http._
import org.specs.Specification
import HttpHeaders._
import HttpMethods._
import HttpStatusCodes._
import MediaTypes._
import Charsets._
import test.SprayTest
import marshalling.{AbstractUnmarshaller, AbstractMarshaller}
import xml.{XML, NodeSeq}

class UnMarshallingBuildersSpec extends Specification with SprayTest with ServiceBuilder {
  
  implicit object IntUnmarshaller extends AbstractUnmarshaller[Int] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`, `ISO-8859-2`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: BufferContent): Int = XML.load(content.inputStream).text.toInt
  }
  
  object IntMarshaller extends AbstractMarshaller[Int] {
    def canMarshalTo = ContentType(`application/xhtml+xml`) :: ContentType(`text/xml`, `UTF-8`) :: Nil
    def marshal(value: Int, contentType: ContentType) = NodeSeqMarshaller.marshal(<int>{value}</int>, contentType)
  }
  
  implicit val marshaller = IntMarshaller orElse DefaultMarshaller
  
  "The 'service' directive" should {
    "not change failure results" in {
      testService(HttpRequest(GET)) {
        service { _.fail(InternalServerError, "NOPE") }
      }.response mustEqual HttpResponse(HttpStatus(InternalServerError, "NOPE"))
    }
    "return EmptyContent unchanged" in {
      testService(HttpRequest(GET)) {
        service { _.complete(EmptyContent) }
      }.response mustEqual HttpResponse()
    }
    "convert ObjectContent to BufferContent using the default marshaller" in {
      testService(HttpRequest(GET)) {
        service { _.complete(<p>yes</p>) }
      }.response.content.as[String] mustEqual Right("<p>yes</p>")
    }
    "convert ObjectContent to BufferContent using the in-scope marshaller" in {
      testService(HttpRequest(GET)) {
        service { _.complete(42) }
      }.response mustEqual HttpResponse(content = HttpContent(ContentType(`application/xhtml+xml`), "<int>42</int>"))
    }
    "return an InternalServerError response if no marshaller is in scope" in {
      testService(HttpRequest(GET)) {
        service { _.complete(42.0) }
      }.response mustEqual failure(InternalServerError, "No marshaller for response content '42.0'")
    }
    "return a NotAcceptable response if no acceptable marshaller is in scope" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service { _.complete(42) }
      }.response mustEqual
              failure(NotAcceptable, "Resource representation is only available with these content-types:\n" +
              "application/xhtml+xml\ntext/xml; charset=UTF-8")
    }
    "let acceptable BufferContent pass" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service { _.complete(HttpContent(`text/css`, "CSS")) }
      }.response mustEqual HttpResponse(content = HttpContent(ContentType(`text/css`), "CSS"))
    }
    "return an InternalServerError if the response BufferContent is not accepted by the client" in {
      testService(HttpRequest(GET, headers = List(`Accept`(`text/css`)))) {
        service { _.complete(HttpContent(`text/plain`, "CSS")) }
      }.response mustEqual failure(InternalServerError, "Response BufferContent has unacceptable Content-Type")
    }
  }
  
  "The 'contentAs' directive" should {
    "convert BufferContent to ObjectContent using the in-scope Unmarshaller" in {
      test(HttpRequest(PUT, content = HttpContent(ContentType(`text/xml`), "<p>cool</p>"))) {
        contentAs[NodeSeq] { ctx => ctx.complete(ctx.request.content.asInstanceOf[ObjectContent].value) }
      }.response.content mustEqual ObjectContent(<p>cool</p>) 
    }
    "return a BadRequest response if the request has no entity" in {
      test(HttpRequest(PUT)) {
        contentAs[NodeSeq] { _ => fail("Should not run") }
      }.response mustEqual failure(BadRequest, "Request entity expected")
    }
    "return an UnsupportedMediaType response if no matching unmarshaller is in scope" in {
      test(HttpRequest(PUT, content = HttpContent(ContentType(`text/css`), "<p>cool</p>"))) {
        contentAs[NodeSeq] { _ => fail("Should not run") }
      }.response mustEqual failure(UnsupportedMediaType, "The requests content-type must be one the following:\n" +
        "text/xml\ntext/html\napplication/xhtml+xml")
    }
  }
  
  "The 'getContentAs' directive" should {
    "extract an object from the requests BufferContent using the in-scope Unmarshaller" in {
      test(HttpRequest(PUT, content = HttpContent(ContentType(`text/xml`), "<p>cool</p>"))) {
        getContentAs[NodeSeq] { xml => _.complete(xml) }
      }.response.content mustEqual ObjectContent(<p>cool</p>) 
    }
    "return a BadRequest response if the request has no entity" in {
      test(HttpRequest(PUT)) {
        getContentAs[NodeSeq] { _ => fail("Should not run") }
      }.response mustEqual failure(BadRequest, "Request entity expected")
    }
    "return an UnsupportedMediaType response if no matching unmarshaller is in scope" in {
      test(HttpRequest(PUT, content = HttpContent(ContentType(`text/css`), "<p>cool</p>"))) {
        getContentAs[NodeSeq] { _ => fail("Should not run") }
      }.response mustEqual failure(UnsupportedMediaType, "The requests content-type must be one the following:\n" +
        "text/xml\ntext/html\napplication/xhtml+xml")
    }
  }
  
  "The 'handledBy' directive" should {
    "support proper round-trip content unmarshalling/marshalling to and from a function" in {
      testService(HttpRequest(PUT, headers = List(Accept(`text/xml`)),
        content = HttpContent(ContentType(`text/html`), "<int>42</int>"))) {
        service { handledBy { (x: Int) => x * 2 } }
      }.response.content mustEqual HttpContent(ContentType(`text/xml`, `UTF-8`), "<int>84</int>")
    }
    "result in an UnsupportedMediaType error if there is no unmarshaller supporting the requests charset" in {
      testService(HttpRequest(PUT, headers = List(Accept(`text/xml`)),
        content = HttpContent(ContentType(`text/xml`, `UTF-8`), "<int>42</int>"))) {
        service { handledBy { (x: Int) => x * 2 } }
      }.response mustEqual failure(UnsupportedMediaType, "The requests content-type must be one the following:\n" +
        "text/xml; charset=ISO-8859-2\ntext/html\napplication/xhtml+xml")
    }
    "result in an NotAcceptable error if there is no marshaller supporting the requests Accept-Charset header" in {
      testService(HttpRequest(PUT, headers = List(Accept(`text/xml`), `Accept-Charset`(`UTF-16`)),
        content = HttpContent(ContentType(`text/html`), "<int>42</int>"))) {
        service { handledBy { (x: Int) => x * 2 } }
      }.response mustEqual failure(NotAcceptable, "Resource representation is only available with these content-types:\n" +
              "application/xhtml+xml\ntext/xml; charset=UTF-8")
    }
  }
  
}