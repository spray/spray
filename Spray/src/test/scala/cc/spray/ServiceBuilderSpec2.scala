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
    "throw FileNotFoundException on non-existing files" in {
      responseFor(HttpRequest(GET)) {
        getFromFile("shshsgfhwrrwhsadsf")
      } must throwA[FileNotFoundException]
    }
    "throw FileNotFoundException on directories" in {
      responseFor(HttpRequest(GET)) {
        getFromFile(Properties.javaHome)
      } must throwA[FileNotFoundException]
    }
    "return the file content with the MimeType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      val response = responseFor(HttpRequest(GET)) {
        getFromFile(file)
      }
      response.contentAsString mustEqual "This is PDF"
      response.responseHeaders mustEqual List(`Content-Type`(`application/pdf`))
      file.delete
    }
    "return the file content with MimeType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("This is some content", file)
      val response = responseFor(HttpRequest(GET)) {
        getFromFile(file)
      }
      response.contentAsString mustEqual "This is some content"
      response.responseHeaders mustEqual List(`Content-Type`(`application/octet-stream`))
      file.delete
    }
  }
  
}