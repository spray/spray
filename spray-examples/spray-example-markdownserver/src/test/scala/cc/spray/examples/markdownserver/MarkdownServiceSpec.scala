package cc.spray.examples.markdownserver

import org.specs.Specification
import cc.spray._
import test._
import http._
import HttpMethods._
import StatusCodes._

class MarkdownServiceSpec extends Specification with SprayTest with MarkdownService with DontDetach {
  
  "The Markdown service" should {
    "return the HTML for the markdown source in the resource file corresponding to the request path" in {
      testService(HttpRequest(GET, "/doc/sample")) {
        markdownService
      }.response.content.as[String] mustEqual Right("<h1>Test</h1>")
    }
    "return a 404 error to paths that do not have corresponding resources" in {
      testService(HttpRequest(GET, "/doc/pipapo")) {
        markdownService
      }.response mustEqual HttpResponse(NotFound)
    }
  }
  
}