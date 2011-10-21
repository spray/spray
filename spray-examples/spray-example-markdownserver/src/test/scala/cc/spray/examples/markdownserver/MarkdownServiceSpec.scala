package cc.spray
package examples.markdownserver

import org.specs2.mutable._
import test._
import http._
import HttpMethods._
import StatusCodes._
import java.util.concurrent.TimeUnit
import akka.util.{Duration => AkkaDuration}

class MarkdownServiceSpec extends Specification with SprayTest with MarkdownService {
  
  "The Markdown service" should {
    "return the HTML for the markdown source in the resource file corresponding to the request path" in {
      testService(HttpRequest(GET, "/doc/sample")) {
        markdownService
      }.response.content.as[String] mustEqual Right("<h1>Test</h1>")
    }
    "return a 404 error to paths that do not have corresponding resources" in {
      testService(HttpRequest(GET, "/doc/pipapo")) {
        markdownService
      }.handled must beFalse
    }
  }

  // since pegdown can sometimes startup a little slow we give the testService a timeout of 2 seconds
  override def testService(request: HttpRequest, timeout: AkkaDuration = AkkaDuration(2, TimeUnit.SECONDS))
                          (service: ServiceTest) = super.testService(request, timeout)(service)

}