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

package cc.spray.can
package rendering

import org.specs2._
import model._
import HttpMethods._

class RequestRendererSpec extends Specification { def is =

    "The request preparation logic should properly render a" ^
      "GET request without headers and without body" ! e1 ^
      "POST request, a few headers and no body" ! e2 ^
      "PUT request, a few headers and a body" ! e3 ^
      "PUT request start (chunked) without body" ! e4 ^
      "POST request start (chunked) with body" ! e5


  def e1 = prep(HttpRequest(method = GET, uri = "/abc")) mustEqual prep {
    """|GET /abc HTTP/1.1
       |Host: test.com:8080
       |User-Agent: spray-can/1.0.0
       |
       |"""
  }

  def e2 = prep {
    HttpRequest(
      method = POST,
      uri = "/abc/xyz",
      headers = List(
        HttpHeader("X-Fancy", "naa"),
        HttpHeader("Age", "0")
      )
    )
  } mustEqual prep {
    """|POST /abc/xyz HTTP/1.1
       |X-Fancy: naa
       |Age: 0
       |Host: test.com:8080
       |User-Agent: spray-can/1.0.0
       |
       |"""
  }

  def e3 = prep {
    HttpRequest(
      method = PUT,
      uri = "/abc/xyz",
      headers = List(
        HttpHeader("X-Fancy", "naa"),
        HttpHeader("Cache-Control", "public")
      ),
      body = "The content please!".getBytes("ISO-8859-1")
    )
  } mustEqual prep {
    """|PUT /abc/xyz HTTP/1.1
       |X-Fancy: naa
       |Cache-Control: public
       |Host: test.com:8080
       |User-Agent: spray-can/1.0.0
       |Content-Length: 19
       |
       |The content please!"""
  }

  def e4 = prep {
    ChunkedRequestStart(HttpRequest(PUT, "/abc/xyz"))
  } mustEqual prep {
    """|PUT /abc/xyz HTTP/1.1
       |Host: test.com:8080
       |User-Agent: spray-can/1.0.0
       |Transfer-Encoding: chunked
       |
       |"""
  }

  def e5 = prep {
    ChunkedRequestStart(HttpRequest(POST, "/abc/xyz").withBody("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
  } mustEqual prep {
    """|POST /abc/xyz HTTP/1.1
       |Host: test.com:8080
       |User-Agent: spray-can/1.0.0
       |Transfer-Encoding: chunked
       |
       |1a
       |ABCDEFGHIJKLMNOPQRSTUVWXYZ
       |"""
  }

  val renderer = new RequestRenderer("spray-can/1.0.0")

  def prep(part: HttpRequestPart) = {
    val RenderedMessagePart(buffers, false) = renderer.render {
      HttpRequestPartRenderingContext(part, "test.com", 8080)
    }
    val sb = new java.lang.StringBuilder()
    buffers.foreach { buf =>
      sb.append(new String(buf.array, "ASCII"))
    }
    sb.toString
  }

  def prep(request: String) = request.stripMargin.replace("\n", "\r\n")
}