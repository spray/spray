/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing

import java.io.File
import org.parboiled.common.FileUtils
import scala.util.Properties
import spray.http._
import MediaTypes._
import HttpHeaders._
import HttpCharsets._


class FileAndResourceDirectivesSpec extends RoutingSpec {

  "getFromFile" should {
    "reject non-GET requests" in {
      Put() ~> getFromFileName("some") ~> check { handled must beFalse }
    }
    "reject requests to non-existing files" in {
      Get() ~> getFromFileName("nonExistentFile") ~> check { handled must beFalse }
    }
    "reject requests to directories" in {
      Get() ~> getFromFileName(Properties.javaHome) ~> check { handled must beFalse }
    }
    "return the file content with the MediaType matching the file extension" in {
      val file = File.createTempFile("sprayTest", ".PDF")
      FileUtils.writeAllText("This is PDF", file)
      Get() ~> getFromFileName(file.getPath) ~> check {
        mediaType === `application/pdf`
        definedCharset === Some(`ISO-8859-1`)
        body.asString === "This is PDF"
        headers === List(`Last-Modified`(DateTime(file.lastModified)))
      }
      file.delete
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      val file = File.createTempFile("sprayTest", null)
      FileUtils.writeAllText("Some content", file)
      Get() ~> getFromFile(file) ~> check {
        mediaType === `application/octet-stream`
        body.asString === "Some content"
      }
      file.delete
    }
    "return a chunked response for files larger than the configured file-chunking-threshold-size" in {
      val file = File.createTempFile("sprayTest2", ".xml")
      FileUtils.writeAllText("<this could be XML if it were formatted correctly>", file)
      Get() ~> getFromFile(file) ~> check {
        mediaType === `text/xml`
        body.asString === "<this co"
        headers === List(`Last-Modified`(DateTime(file.lastModified)))
        chunks.map(_.bodyAsString).mkString("|") === "uld be X|ML if it| were fo|rmatted |correctl|y>"
      }
      file.delete
    }
  }

  "getFromResource" should {
    "reject non-GET requests" in {
      Put() ~> getFromResource("some") ~> check { handled must beFalse }
    }
    "reject requests to non-existing resources" in {
      Get() ~> getFromResource("nonExistingResource") ~> check { handled must beFalse }
    }
    "return the resource content with the MediaType matching the file extension" in {
      Get() ~> getFromResource("sample.html") ~> check {
        mediaType === `text/html`
        body.asString === "<p>Lorem ipsum!</p>"
        headers must have {
          case `Last-Modified`(dt) => DateTime(2011, 7, 1) < dt && dt.clicks < System.currentTimeMillis()
        }
      }
    }
    "return the file content with MediaType 'application/octet-stream' on unknown file extensions" in {
      Get() ~> getFromResource("sample.xyz") ~> check {
        mediaType === `application/octet-stream`
        body.asString === "XyZ"
      }
    }
  }

  "getFromResourceDirectory" should {
    "reject requests to non-existing resources" in {
      Get("not/found") ~> getFromResourceDirectory("subDirectory") ~> check { handled must beFalse }
    }
    "return the resource content with the MediaType matching the file extension" in {
      val verify = check {
        mediaType === `application/pdf`
        body.asString === ""
      }
      "example 1" in { Get("empty.pdf") ~> getFromResourceDirectory("subDirectory") ~> verify }
      "example 2" in { Get("empty.pdf") ~> getFromResourceDirectory("subDirectory/") ~> verify }
      "example 3" in { Get("subDirectory/empty.pdf") ~> getFromResourceDirectory("") ~> verify }
    }
    "reject requests to directory resources" in {
      Get() ~> getFromResourceDirectory("subDirectory") ~> check { handled must beFalse }
    }
  }

}