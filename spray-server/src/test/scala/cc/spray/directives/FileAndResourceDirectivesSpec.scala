/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray
package directives

import http._
import HttpMethods._
import MediaTypes._
import HttpHeaders._
import HttpCharsets._
import org.parboiled.common.FileUtils
import util.Properties
import java.io.File
import test.AbstractSprayTest

class FileAndResourceDirectivesSpec extends AbstractSprayTest {

  "getFromFile" should {
    "reject non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromFileName("some")
      }.handled must beFalse
    }
    "reject requests to non-existing files" in {
      test(HttpRequest(GET)) {
        getFromFileName("nonExistentFile")
      }.handled must beFalse
    }
    "reject requests to directories" in {
      test(HttpRequest(GET)) {
        getFromFileName(Properties.javaHome)
      }.handled must beFalse
    }
    "return the file content with the MediaType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      val response = test(HttpRequest(GET)) {
        getFromFileName(file.getPath)
      }.response
      response.content mustEqual Some(HttpContent(`application/pdf`, "This is PDF"))
      response.headers mustEqual List(`Last-Modified`(DateTime(file.lastModified)))
      file.delete
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      test(HttpRequest(GET)) {
        getFromFile(file)
      }.response.content mustEqual Some(HttpContent(`application/octet-stream`, "Some content"))
      file.delete
    }
    "return a chunked response for files larger than the configured file-chunking-threshold-size" in {
      val file = File.createTempFile("sprayTest2", ".xml")
      FileUtils.writeAllText("<this could be XML if it were formatted correctly>", file)
      val result = test(HttpRequest(GET)) {
        getFromFile(file)
      }
      result.response.content mustEqual Some(HttpContent(ContentType(`text/xml`, `ISO-8859-1`), ""))
      result.response.headers mustEqual List(`Last-Modified`(DateTime(file.lastModified)))
      result.chunks.map(_.bodyAsString).mkString("|") mustEqual
        "<this co|uld be X|ML if it| were fo|rmatted |correctl|y>"
      file.delete
    }
  }
  
  "getFromResource" should {
    "reject non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromResource("some")
      }.handled must beFalse
    }
    "reject requests to non-existing resources" in {
      test(HttpRequest(GET)) {
        getFromResource("nonExistingResource")
      }.handled must beFalse
    }
    "return the resource content with the MediaType matching the file extension" in {
      val HttpResponse(_, headers, content, _) = test(HttpRequest(GET)) {
        getFromResource("sample.html")
      }.response
      content mustEqual Some(HttpContent(`text/html`, "<p>Lorem ipsum!</p>"))
      headers must have { case `Last-Modified`(dt) => DateTime(2011, 7, 1) < dt && dt.clicks < System.currentTimeMillis() }
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.xyz")
      }.response.content mustEqual Some(HttpContent(`application/octet-stream`, "XyZ"))
    }
  }
  
  "getFromResourceDirectory" should {
    "reject requests to non-existing resources" in {
      test(HttpRequest(GET, "not/found")) {
        getFromResourceDirectory("subDirectory")
      }.handled must beFalse
    }
    "return the resource content with the MediaType matching the file extension" in {
      "example 1" in {
        test(HttpRequest(GET, "empty.pdf")) {
          getFromResourceDirectory("subDirectory")
        }.response.content mustEqual Some(HttpContent(`application/pdf`, ""))
      }
      "example 2" in {
        test(HttpRequest(GET, "subDirectory/empty.pdf")) {
          getFromResourceDirectory("")
        }.response.content mustEqual Some(HttpContent(`application/pdf`, ""))
      }
    }
  }
  
}