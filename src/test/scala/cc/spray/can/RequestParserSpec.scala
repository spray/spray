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

import org.specs2.mutable.Specification
import java.nio.ByteBuffer
import annotation.tailrec
import Constants._

class RequestParserSpec extends Specification {

  val HH = HttpHeader

  "The request parsing logic" should {
    "properly parse a request" in {
      "without headers and body" in {
        test {
          """|GET / HTTP/1.1
             |
             |"""
        } mustEqual ("GET", "/", Nil, "")
      }

      "with one header" in {
        test {
          """|GET / HTTP/1.1
             |Host: api.example.com
             |
             |"""
        } mustEqual ("GET", "/", List(HH("Host", "api.example.com")), "")
      }

      "with 3 headers and a body" in {
        test {
          """|POST /resource/yes HTTP/1.1
             |User-Agent: curl/7.19.7 xyz
             |Transfer-Encoding:identity
             |Content-Length    : 17
             |
             |Shake your BOODY!"""
        } mustEqual ("POST", "/resource/yes", List(
          HH("Content-Length", "17"),
          HH("Transfer-Encoding", "identity"),
          HH("User-Agent", "curl/7.19.7 xyz")
        ), "Shake your BOODY!")
      }

      "with multi-line headers" in {
        test {
          """|DELETE /abc HTTP/1.1
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |
             |"""
        } mustEqual ("DELETE", "/abc", List(
          HH("Accept", "*/*"),
          HH("User-Agent", "curl/7.19.7 abc xyz")
        ), "")
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        test("get") mustEqual ErrorRequestParser(501, "Unsupported HTTP method")
        test("GETX") mustEqual ErrorRequestParser(501, "Unsupported HTTP method")
      }

      "an URI longer than 2048 chars" in {
        test("GET x" + "xxxx" * 512) mustEqual ErrorRequestParser(414, "URIs with more than 2048 characters are not supported by this server")
      }

      "HTTP version 1.0" in {
        test("GET / HTTP/1.0\r\n") mustEqual ErrorRequestParser(400, "Http version 'HTTP/1.0' not supported")
      }

      "with an illegal char in a header name" in {
        test {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } mustEqual ErrorRequestParser(400, "Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        test {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } mustEqual ErrorRequestParser(400, "HTTP headers with names longer than 64 characters are not supported by this server")
      }

      "with a non-identity transfer encoding" in {
        test {
          """|GET / HTTP/1.1
             |Transfer-Encoding: chunked
             |
             |abc"""
        } mustEqual ErrorRequestParser(501, "Non-identity transfer encodings are not currently supported by this server")
      }

      "with a header-value longer than 8192 chars" in {
        test {
          """|GET / HTTP/1.1
             |Fancy: 0""" + ("12345678" * 1024)
        } mustEqual ErrorRequestParser(400, "HTTP header values longer than 8192 characters are not supported by this server (header 'Fancy')")
      }

      "with an invalid Content-Length header value" in {
        test {
          """|GET / HTTP/1.1
             |Content-Length: 1.5
             |
             |abc"""
        } mustEqual ErrorRequestParser(400, "Invalid Content-Length header value")
        test {
          """|GET / HTTP/1.1
             |Content-Length: -3
             |
             |abc"""
        } mustEqual ErrorRequestParser(400, "Invalid Content-Length header value")
        test {
          """|GET / HTTP/1.1
             |Content-Length: 3
             |
             |abcde"""
        } mustEqual ErrorRequestParser(400, "Illegal Content-Length: request entity is longer than Content-Length header value")
      }
    }
  }

  def test(request: String) = {
    val req = request.stripMargin.replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes(US_ASCII))

    @tailrec
    def runAgainst(req: RequestParser): Any = req.read(buf) match {
      case CompleteRequestParser(method, uri, headers, body) => (method, uri, headers, new String(body, US_ASCII))
      case x: ErrorRequestParser => x
      case x => runAgainst(x)
    }

    runAgainst(EmptyRequestParser)
  }

}