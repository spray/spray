package cc.spray
package builders

import org.specs.Specification
import http._
import HttpMethods._
import HttpHeaders._
import MimeTypes._
import org.parboiled.common.FileUtils
import util.Properties
import java.io.File
import test.{DetachingDisabled, SprayTest}

class FileResourceDirectoryBuildersSpec extends Specification with DetachingDisabled with SprayTest {

  "getFromFile" should {
    "block non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromFile("some")
      }.handled must beFalse
    }
    "return a 404 for non-existing files" in {
      test(HttpRequest(GET)) {
        getFromFile("nonExistentFile")
      }.response mustEqual failure(404)
    }
    "return a 404 for directories" in {
      test(HttpRequest(GET)) {
        getFromFile(Properties.javaHome)
      }.response mustEqual failure(404)
    }
    "return the file content with the MimeType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      test(HttpRequest(GET)) {
        getFromFile(file.getPath)
      }.response mustEqual HttpResponse(content = HttpContent(`application/pdf`, "This is PDF"))
      file.delete
    }
    "return the file content with MimeType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      test(HttpRequest(GET)) {
        getFromFile(file.getPath)
      }.response mustEqual HttpResponse(content = HttpContent(`application/octet-stream`, "Some content"))
      file.delete
    }
  }
  
  "getFromResource" should {
    "block non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromResource("some")
      }.handled must beFalse
    }
    "return a 404 for non-existing resources" in {
      test(HttpRequest(GET)) {
        getFromResource("nonExistingResource")
      }.response mustEqual failure(404)
    }
    "return the resource content with the MimeType matching the file extension" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.html")
      }.response mustEqual HttpResponse(content = HttpContent(`text/html`, "<p>Lorem ipsum!</p>"))
    }
    "return the file content with MimeType 'application/octet-stream' on unknown file extensions" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.xyz")
      }.response mustEqual HttpResponse(content = HttpContent(`application/octet-stream`, "XyZ"))
    }
  }
  
  "getFromResourceDirectory" should {
    "return a 404 for non-existing resources" in {
      test(HttpRequest(GET, "/not/found")) {
        getFromResourceDirectory("subDirectory")
      }.response mustEqual failure(404)
    }
    "return the resource content with the MimeType matching the file extension" in {
      test(HttpRequest(GET, "subDirectory/empty.pdf")) {
        getFromResourceDirectory("")
      }.response mustEqual HttpResponse(content = HttpContent(`application/pdf`, ""))
    }
  }
  
}