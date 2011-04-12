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

import org.specs.mock.Mockito
import org.specs.Specification
import scala.collection.JavaConversions._
import javax.servlet.http.HttpServletRequest
import javax.servlet.ServletInputStream
import java.io.ByteArrayInputStream
import http._
import MediaTypes._
import HttpHeaders._
import HttpMethods._
import Charsets._

class ServletConverterSpec extends Specification with Mockito {
  val convert = new ServletConverter {}
  
  "The ServletConverter" should {
    "properly convert a minimal request" in (
      convert.toSprayRequest(Hsr("GET", "/path"))
        mustEqual
      HttpRequest(GET, "/path")
    )
    
    "properly convert a request with headers" in (
      convert.toSprayRequest(Hsr("POST", "/path", "", Map("Accept" -> "text/html", "Accept-Charset" -> "utf8")))
        mustEqual
      HttpRequest(POST, "/path", List(Accept(`text/html`), `Accept-Charset`(`UTF-8`)))
    )
    
    "properly convert a request with query parameters" in (
      convert.toSprayRequest(Hsr("GET", "/path", "key=value"))
        mustEqual
      HttpRequest(GET, "/path?key=value")
    )
    
    "create an HttpContent instance in the HttpRequest" in {
      "that has the MediaType of the requests Content-Type header and remove the Content-Type header" in (
        convert.toSprayRequest(Hsr("POST", "/path", "", Map("Content-Type" -> "application/json"), "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(`application/json`, "yes".getBytes)))
      )
      "and mark the content as 'application/octet-stream' if no Content-Type header is present" in (
        convert.toSprayRequest(Hsr("POST", "/path", content = "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(`application/octet-stream`, "yes".getBytes)))
      )
      "and carry over explicitly given charset from the Content-Type header" in (
        convert.toSprayRequest(Hsr("POST", "/path", "", Map("Content-Type" -> "text/css; charset=utf8"), "yes"))
          mustEqual
        HttpRequest(method = POST, uri = "/path", content = Some(HttpContent(ContentType(`text/css`, `UTF-8`), "yes".getBytes)))
      )
    }
  }
  
  private def Hsr(method: String, uri: String, queryString: String = "", headers: Map[String, String] = Map(),
                  content: String = "") = {
    import java.util.Enumeration    
    make(mock[HttpServletRequest]) { hsr =>
      hsr.getMethod returns method
      hsr.getRequestURL returns new StringBuffer(uri)
      hsr.getQueryString returns queryString
      hsr.getHeaderNames.asInstanceOf[Enumeration[String]] returns headers.keysIterator
      for ((key, value) <- headers) {
        hsr.getHeaders(key).asInstanceOf[Enumeration[String]] returns Iterator(value)
      }
      hsr.getInputStream returns make(mock[ServletInputStream]) { mock =>
        val stream = new ByteArrayInputStream(content.getBytes)
        mock.read(any) answers { array => stream.read(array.asInstanceOf[Array[Byte]])} 
      }
      hsr.getRemoteAddr returns "a:b" // provoke an UnknownHostException 
      hsr.getProtocol returns "HTTP/1.1"
    }
  }
  
}