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
import org.parboiled.common.FileUtils
import util.Properties
import java.io.File
import test.AbstractSprayTest

class FileAndResourceDirectivesSpec extends AbstractSprayTest {

  "getFromFile" should {
    "block non-GET requests" in {
      test(HttpRequest(PUT)) {
        getFromFileName("some")
      }.handled must beFalse
    }
    "return a 404 for non-existing files" in {
      test(HttpRequest(GET)) {
        getFromFileName("nonExistentFile")
      }.response mustEqual HttpResponse(404)
    }
    "return a 404 for directories" in {
      test(HttpRequest(GET)) {
        getFromFileName(Properties.javaHome)
      }.response mustEqual HttpResponse(404)
    }
    "return the file content with the MediaType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      test(HttpRequest(GET)) {
        getFromFileName(file.getPath)
      }.response.content mustEqual Some(HttpContent(`application/pdf`, "This is PDF"))
      file.delete
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      test(HttpRequest(GET)) {
        getFromFileName(file.getPath)
      }.response.content mustEqual Some(HttpContent(`application/octet-stream`, "Some content"))
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
      }.response mustEqual HttpResponse(404)
    }
    "return the resource content with the MediaType matching the file extension" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.html")
      }.response.content mustEqual Some(HttpContent(`text/html`, "<p>Lorem ipsum!</p>"))
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      test(HttpRequest(GET)) {
        getFromResource("sample.xyz")
      }.response.content mustEqual Some(HttpContent(`application/octet-stream`, "XyZ"))
    }
  }
  
  "getFromResourceDirectory" should {
    "return a 404 for non-existing resources" in {
      test(HttpRequest(GET, "not/found")) {
        getFromResourceDirectory("subDirectory")
      }.response mustEqual HttpResponse(404)
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