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
    "return a 500 for non-existing files" in {
      test(HttpRequest(GET)) {
        getFromFile("nonExistentFile")
      }.response.status mustEqual HttpStatus(404, "File 'nonExistentFile' not found")
    }
    "return a 500 for directories" in {
      test(HttpRequest(GET)) {
        getFromFile(Properties.javaHome)
      }.response.status mustEqual HttpStatus(404, "File '" + Properties.javaHome + "' not found")
    }
    "return the file content with the MimeType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      test(HttpRequest(GET)) {
        getFromFile(file)
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`application/pdf`)), content = "This is PDF")
      file.delete
    }
    "return the file content with MimeType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      test(HttpRequest(GET)) {
        getFromFile(file)
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`application/octet-stream`)), content = "Some content")
      file.delete
    }
  }
  
  "getFromResource" should {
    "block non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromResource("some")
      }.handled must beFalse
    }
    "return a 500 for non-existing resources" in {
      test(HttpRequest(GET)) {
        getFromResource("nonExistingResource")
      }.response.status mustEqual HttpStatus(404, "Resource 'nonExistingResource' not found")
    }
    "return the resource content with the MimeType matching the file extension" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.html")
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`text/html`)), content = "<p>Lorem ipsum!</p>")
    }
    "return the file content with MimeType 'application/octet-stream' on unknown file extensions" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.xyz")
      }.response mustEqual HttpResponse(headers = List(`Content-Type`(`application/octet-stream`)), content = "XyZ")
    }
  }

}