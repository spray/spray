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

import org.specs.Specification
import scala.collection.JavaConversions._
import http._
import MediaTypes._
import HttpHeaders._
import HttpMethods._
import HttpCharsets._
import StatusCodes._
import utils.CantWriteResponseBodyException
import java.io.{IOException, OutputStream, ByteArrayOutputStream, ByteArrayInputStream}

class ToFromRawConverterSpec extends Specification {
  val convert = new ToFromRawConverter {
    val addConnectionCloseResponseHeader = true
  }
  
  "The ToFromRawConverter" should {
    "properly convert a minimal request" in (
      convert.toSprayRequest(raw("GET", "/path"))
        mustEqual
      HttpRequest(GET, "/path")
    )
    
    "properly convert a request with headers" in (
      convert.toSprayRequest(raw("POST", "/path", Map("Accept" -> "text/html", "Accept-Charset" -> "utf8")))
        mustEqual
      HttpRequest(POST, "/path", List(Accept(`text/html`), `Accept-Charset`(`UTF-8`)))
    )
    
    "properly convert a request with query parameters" in (
      convert.toSprayRequest(raw("GET", "/path?key=value"))
        mustEqual
      HttpRequest(GET, "/path?key=value")
    )
    
    "create an HttpContent instance in the HttpRequest" in {
      "that has the MediaType of the requests Content-Type header and remove the Content-Type header" in (
        convert.toSprayRequest(raw("POST", "/path", Map("Content-Type" -> "application/json", "Content-Length" -> "10"), "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(`application/json`, "yes".getBytes)))
      )
      "and mark the content as 'application/octet-stream' if no Content-Type header is present" in (
        convert.toSprayRequest(raw("POST", "/path", Map("Content-Length" -> "10"), "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(`application/octet-stream`, "yes".getBytes)))
      )
      "and carry over explicitly given charset from the Content-Type header" in (
        convert.toSprayRequest(raw("POST", "/path", Map("Content-Type" -> "text/css; charset=utf8", "Content-Length" -> "10"), "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(ContentType(`text/css`, `UTF-8`), "yes".getBytes)))
      )
    }
    
    "properly write a response into a RawResponse" in {
      var status = 0
      val headers = collection.mutable.Map.empty[String, String]
      val out = new ByteArrayOutputStream
      convert.fromSprayResponse(HttpResponse(OK, List(`Content-Encoding`(HttpEncodings.gzip), Location("xyz")),
              Some(HttpContent("hello")))) {
        new RawResponse {
          def setStatus(code: Int) { status = code }
          def addHeader(name: String, value: String) { headers.update(name, value) }
          def outputStream = out
        }
      }
      status mustEqual 200
      headers mustEqual Map("Content-Encoding" -> "gzip", "Location" -> "xyz", "Content-Length" -> "5",
        "Content-Type" -> "text/plain", "Connection" -> "close")
      new String(out.toByteArray, "ISO-8859-1") mustEqual "hello"
    }
    
    "throw a CantWriteResponseBodyException if the RawResponses output stream has already been closed" in {
      convert.fromSprayResponse(HttpResponse(content = Some(HttpContent("hello")))) {
        new RawResponse {
          def setStatus(code: Int) {}
          def addHeader(name: String, value: String) {}
          def outputStream = new OutputStream { def write(b: Int) { throw new IOException("Closed") } }
        }
      } must throwA[CantWriteResponseBodyException]      
    }
  }
  
  private def raw(m: String, u: String, h: Map[String, String] = Map(), content: String = "") = new RawRequest {
    def method = m
    def uri = u
    def headers = h
    def inputStream = new ByteArrayInputStream(content.getBytes)
    def remoteIP = "a:b" // provoke an UnknownHostException
    def protocol = "HTTP/1.1"
  }
  
}