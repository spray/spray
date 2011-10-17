package cc.spray
package examples.stopwatch

import org.specs2.mutable._
import test._
import http._
import HttpMethods._
import StatusCodes._
import MediaTypes._

class StopWatchServiceSpec extends Specification with SprayTest with StopWatchService {
  args(sequential = true)
  
  override def currentTime = 12000L // use a fixed current time for testing
  
  "The StopWatch service" should {
    "return the content of the 'index.html' resource for GET requests to the root path" in {
      testService(HttpRequest(GET, "/")) {
        stopWatchService
      }.response.content mustEqual Some(HttpContent(`text/html`, "<h1>test content</h1>"))
    }
    "return an empty list for GET requests to /watches" in {
      testService(HttpRequest(GET, "/watches")) {
        stopWatchService
      }.response.content.as[String].right.get must contain("No watches")
    }
    "create a new watch upon a POST request to /watches" in {
      testService(HttpRequest(POST, "/watches")) {
        stopWatchService
      }.response.content.as[String].right.get must contain("New stopwatch created")
    }
    "create a new watch upon a GET request to /watches?method=post" in {
      testService(HttpRequest(GET, "/watches?method=post")) {
        stopWatchService
      }.response.content.as[String].right.get must contain("New stopwatch created")
    }
    "return a representation of the watch upon a GET requests to /watch/0" in {
      testService(HttpRequest(POST, "/watches")) {
        stopWatchService
      }.response.content.as[String].right.get must contain("New stopwatch created")
      testService(HttpRequest(GET, "/watch/0/")) {
        stopWatchService
      }.response.content.as[String].right.get must contain("stopped")
    }
    "leave GET requests to other paths unhandled" in {
      testService(HttpRequest(GET, "/kermit")) {
        stopWatchService
      }.handled must beFalse
    }
    "return a MethodNotAllowed error for POST requests to the root path" in {
      testService(HttpRequest(POST, "/")) {
        stopWatchService
      }.response mustEqual HttpResponse(MethodNotAllowed, "HTTP method not allowed, supported methods: GET")
    }
  }

}