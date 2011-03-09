package cc.spray

import http._
import HttpMethods._
import java.io.{FileNotFoundException, File}
import util.Properties
import org.parboiled.common.FileUtils
import MimeTypes._
import HttpHeaders._

trait ServiceBuilderSpec2 {
  this: ServiceBuilderSpec =>

  "getFromFile" should {
    "block non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromFile("some")
      }.handled must beFalse
    }
    "return a 500 for non-existing files" in {
      test(HttpRequest(GET)) {
        getFromFile("nonExistentFile")
      }.response.status mustEqual HttpStatus(500, "File 'nonExistentFile' not found")
    }
    "return a 500 for directories" in {
      test(HttpRequest(GET)) {
        getFromFile(Properties.javaHome)
      }.response.status mustEqual HttpStatus(500, "File '" + Properties.javaHome + "' not found")
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
      }.response.status mustEqual HttpStatus(500, "Resource 'nonExistingResource' not found")
    }
  }
  
}