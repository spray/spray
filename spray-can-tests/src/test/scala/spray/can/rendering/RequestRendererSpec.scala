/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.can
package rendering

import java.net.InetSocketAddress
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.event.NoLogging
import spray.http._
import spray.util._
import HttpHeaders._
import HttpMethods._
import MediaTypes._

class RequestRendererSpec extends Specification {

  "The request preparation logic" should {
    "properly render a" in {

      "GET request without headers and without body" in new TestSetup() {
        HttpRequest(GET, "/abc") must beRenderedTo {
          """|GET /abc HTTP/1.1
             |Host: test.com:8080
             |User-Agent: spray-can/1.0.0
             |
             |"""
        }
      }

      "GET request with a URI that requires encoding" in new TestSetup() {
        HttpRequest(GET, "/abc<def") must beRenderedTo {
          """|GET /abc%3Cdef HTTP/1.1
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |
            |"""
        }
      }

      "POST request, a few headers (incl. a custom Host header) and no body" in new TestSetup() {
        HttpRequest(POST, "/abc/xyz", List(
          RawHeader("X-Fancy", "naa"),
          RawHeader("Age", "0"),
          Host("spray.io", 9999))) must beRenderedTo {
          """|POST /abc/xyz HTTP/1.1
             |X-Fancy: naa
             |Age: 0
             |Host: spray.io:9999
             |User-Agent: spray-can/1.0.0
             |Content-Length: 0
             |
             |"""
        }
      }

      "PUT request, a few headers and a body" in new TestSetup() {
        HttpRequest(PUT, "/abc/xyz", List(
          RawHeader("X-Fancy", "naa"),
          RawHeader("Cache-Control", "public"),
          Host("spray.io"))).withEntity("The content please!") must beRenderedTo {
          """|PUT /abc/xyz HTTP/1.1
             |X-Fancy: naa
             |Cache-Control: public
             |Host: spray.io
             |User-Agent: spray-can/1.0.0
             |Content-Type: text/plain; charset=UTF-8
             |Content-Length: 19
             |
             |The content please!"""
        }
      }

      "PUT request, a few headers and a body with suppressed content type" in new TestSetup() {
        HttpRequest(PUT, "/abc/xyz", List(
          RawHeader("X-Fancy", "naa"),
          RawHeader("Cache-Control", "public"),
          Host("spray.io"))).withEntity(HttpEntity(ContentTypes.NoContentType, "The content please!")) must beRenderedTo {
          """|PUT /abc/xyz HTTP/1.1
             |X-Fancy: naa
             |Cache-Control: public
             |Host: spray.io
             |User-Agent: spray-can/1.0.0
             |Content-Length: 19
             |
             |The content please!"""
        }
      }

      "PUT request start (chunked) without body but custom Content-Type" in new TestSetup() {
        ChunkedRequestStart(HttpRequest(PUT, "/abc/xyz", List(`Content-Type`(`text/plain`)))) must beRenderedTo {
          """PUT /abc/xyz HTTP/1.1
            |Content-Type: text/plain
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |Transfer-Encoding: chunked
            |
            |"""
        }
      }

      "POST request start (chunked) with body" in new TestSetup() {
        ChunkedRequestStart(HttpRequest(POST, "/abc/xyz")
          .withEntity("ABCDEFGHIJKLMNOPQRSTUVWXYZ")) must beRenderedTo {
          """|POST /abc/xyz HTTP/1.1
             |Host: test.com:8080
             |User-Agent: spray-can/1.0.0
             |Content-Type: text/plain; charset=UTF-8
             |Transfer-Encoding: chunked
             |
             |1a
             |ABCDEFGHIJKLMNOPQRSTUVWXYZ
             |"""
        }
      }
      "POST request start with HTTP/1.0" in new TestSetup() {
        ChunkedRequestStart(
          HttpRequest(POST,
            protocol = HttpProtocols.`HTTP/1.0`,
            uri = "/abc/xyz",
            headers = List(RawHeader("Age", "30"), `Content-Type`(`text/plain`), `Content-Length`(1000)))) must beRenderedTo {
            """POST /abc/xyz HTTP/1.0
            |Age: 30
            |Content-Type: text/plain
            |Content-Length: 1000
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |
            |"""
          }
      }
      "POST request start (chunkless chunk) without Content-Length must throw" in new TestSetup(chunklessStreaming = true) {
        render(ChunkedRequestStart(HttpRequest(POST, uri = "/abc/xyz", headers = List(RawHeader("Age", "30"), `Content-Type`(`text/plain`))))) must
          throwA[RuntimeException].like {
            case e: RuntimeException ⇒ e.getMessage === "Chunkless streamed request is missing user-specified Content-Length header"
          }
      }

      "POST request start (chunkless chunk) with body and explicit Content-Length" in new TestSetup(chunklessStreaming = true) {
        ChunkedRequestStart(HttpRequest(POST, uri = "/abc/xyz", entity = "Yahoooo", headers = List(`Content-Length`(1000)))) must beRenderedTo {
          """POST /abc/xyz HTTP/1.1
            |Content-Length: 1000
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |Content-Type: text/plain; charset=UTF-8
            |
            |Yahoooo"""
        }
      }

      "a chunkless request chunk" in new TestSetup(chunklessStreaming = true) {
        MessageChunk(HttpData("body123".getBytes)) must beRenderedTo {
          "body123"
        }
      }

      "a chunkless final request chunk" in new TestSetup(chunklessStreaming = true) {
        ChunkedMessageEnd("",
          List(RawHeader("Age", "30"), RawHeader("Cache-Control", "public"))) must beRenderedTo {
            ""
          }
      }
    }

    "properly handle the User-Agent header" in {

      "GET request without headers and without body" in new TestSetup() {
        HttpRequest(GET, "/abc") must beRenderedTo {
          """GET /abc HTTP/1.1
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |
            |"""
        }
      }

      "GET request with overridden User-Agent and without body" in new TestSetup(None) {
        HttpRequest(GET, "/abc", List(`User-Agent`("blah-blah/1.0"))) must beRenderedTo {
          """GET /abc HTTP/1.1
            |User-Agent: blah-blah/1.0
            |Host: test.com:8080
            |
            |"""
        }
      }

      "GET request with overridden User-Agent and without body" in new TestSetup(Some(`User-Agent`("settings-ua/1.0"))) {
        HttpRequest(GET, "/abc", List(`User-Agent`("user-ua/1.0"))) must beRenderedTo {
          """GET /abc HTTP/1.1
            |User-Agent: user-ua/1.0
            |Host: test.com:8080
            |
            |"""
        }
      }
    }

    "properly uses URI from Raw-Request-URI header if present" in {
      "GET request with Raw-Request-URI" in new TestSetup() {
        HttpRequest(GET, "/abc", List(`Raw-Request-URI`("/def"))) must beRenderedTo {
          """GET /def HTTP/1.1
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |
            |"""
        }
      }

      "GET request with Raw-Request-URI sends raw URI even with invalid utf8 characters" in new TestSetup() {
        HttpRequest(GET, "/abc", List(`Raw-Request-URI`("/def%80%fe%ff"))) must beRenderedTo {
          """GET /def%80%fe%ff HTTP/1.1
            |Host: test.com:8080
            |User-Agent: spray-can/1.0.0
            |
            |"""
        }
      }
    }
  }

  class TestSetup(val userAgent: Option[`User-Agent`] = Some(`User-Agent`("spray-can/1.0.0")), val chunklessStreaming: Boolean = false)
      extends RequestRenderingComponent with Scope {
    def beRenderedTo(content: String) = beEqualTo(content.stripMarginWithNewline("\r\n")) ^^ (render _)

    def render(part: HttpRequestPart): String = {
      val r = new ByteStringRendering(256)
      val protocol = part match {
        case msg: HttpMessageStart ⇒ msg.message.protocol
        case _                     ⇒ HttpProtocols.`HTTP/1.1`
      }
      renderRequestPartRenderingContext(r,
        RequestPartRenderingContext(part, requestProtocol = protocol),
        new InetSocketAddress("test.com", 8080),
        NoLogging)
      r.get.utf8String
    }
  }
}
