/*
 * Copyright (C) 2011-2013 spray.io
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
import akka.spray.NoLogging
import spray.util.EOL
import spray.http._
import HttpHeaders._
import HttpMethods._
import org.specs2.specification.Scope

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

      "POST request, a few headers (incl. a custom Host header) and no body" in new TestSetup() {
        HttpRequest(POST, "/abc/xyz", List(
          RawHeader("X-Fancy", "naa"),
          RawHeader("Age", "0"),
          RawHeader("Host", "spray.io:9999"))) must beRenderedTo {
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
          RawHeader("Host", "spray.io"))).withEntity("The content please!") must beRenderedTo {
          """|PUT /abc/xyz HTTP/1.1
             |X-Fancy: naa
             |Cache-Control: public
             |Host: spray.io
             |User-Agent: spray-can/1.0.0
             |Content-Type: text/plain
             |Content-Length: 19
             |
             |The content please!"""
        }
      }

      "PUT request start (chunked) without body" in new TestSetup() {
        ChunkedRequestStart(HttpRequest(PUT, "/abc/xyz")) must beRenderedTo {
          """|PUT /abc/xyz HTTP/1.1
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
             |Content-Type: text/plain
             |Transfer-Encoding: chunked
             |
             |1a
             |ABCDEFGHIJKLMNOPQRSTUVWXYZ
             |"""
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
        HttpRequest(GET, "/abc", List(RawHeader("User-Agent", "blah-blah/1.0"))) must beRenderedTo {
          """GET /abc HTTP/1.1
            |User-Agent: blah-blah/1.0
            |Host: test.com:8080
            |
            |"""
        }
      }

      "GET request with overridden User-Agent and without body" in new TestSetup(Some(`User-Agent`("settings-ua/1.0"))) {
        HttpRequest(GET, "/abc", List(RawHeader("User-Agent", "user-ua/1.0"))) must beRenderedTo {
          """GET /abc HTTP/1.1
            |Host: test.com:8080
            |User-Agent: settings-ua/1.0
            |
            |"""
        }
      }
    }
  }

  class TestSetup(val userAgent: Option[`User-Agent`] = Some(`User-Agent`("spray-can/1.0.0")))
      extends RequestRenderingComponent with Scope {
    def beRenderedTo(content: String) = {
      beEqualTo(content.stripMargin.replace(EOL, "\r\n")) ^^ { part: HttpRequestPart ⇒
        val r = new ByteStringRendering(256)
        renderRequestPart(r, part, new InetSocketAddress("test.com", 8080), NoLogging)
        r.get.utf8String
      }
    }
  }
}
