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
import HttpProtocols._

class ResponseParserSpecs extends Specification {

  "The reponse parsing logic" should {
    "properly parse a response" in {
      "without headers and body" in {
        parse {
          """|HTTP/1.1 200 OK
             |
             |"""
        } mustEqual (`HTTP/1.1`, 200, "OK", Nil, None, "")
      }

      "with one header" in {
        parse {
          """|HTTP/1.0 404 Not Found
             |Host: api.example.com
             |
             |"""
        } mustEqual (`HTTP/1.0`, 404, "Not Found", List(HttpHeader("Host", "api.example.com")), None, "")
      }

      "with 4 headers and a body" in {
        parse {
          """|HTTP/1.1 500 Internal Server Error
             |User-Agent: curl/7.19.7 xyz
             |Transfer-Encoding:identity
             |Connection:close
             |Content-Length    : 17
             |
             |Shake your BOODY!"""
        } mustEqual (`HTTP/1.1`, 500, "Internal Server Error", List(
          HttpHeader("Content-Length", "17"),
          HttpHeader("Connection", "close"),
          HttpHeader("Transfer-Encoding", "identity"),
          HttpHeader("User-Agent", "curl/7.19.7 xyz")
        ), Some("close"), "Shake your BOODY!")
      }

      "with multi-line headers" in {
        parse {
          """|HTTP/1.1 200 OK
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |
             |"""
        } mustEqual (`HTTP/1.1`, 200, "OK", List(
          HttpHeader("Accept", "*/*"),
          HttpHeader("User-Agent", "curl/7.19.7 abc xyz")
        ), None, "")
      }
    }

    "reject a response with" in {
      "HTTP version 1.2" in {
        parse("HTTP/1.2 200 OK\r\n") mustEqual ErrorMessageParser("HTTP Version not supported", 505)
      }

      "an illegal status code" in {
        parse("HTTP/1.1 700 Something") mustEqual ErrorMessageParser("Illegal response status code")
        parse("HTTP/1.1 2000 Something") mustEqual ErrorMessageParser("Illegal response status code")
      }

      "a response status reason longer than 64 chars" in {
        parse("HTTP/1.1 250 x" + "xxxx" * 16 + "\r\n") mustEqual
                ErrorMessageParser("Reason phrases with more than 64 characters are not supported")
      }

      "with an illegal char in a header name" in {
        parse {
          """|HTTP/1.1 200 OK
             |User@Agent: curl/7.19.7"""
        } mustEqual ErrorMessageParser("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } mustEqual ErrorMessageParser("HTTP headers with names longer than 64 characters are not supported")
      }

      "with a non-identity transfer encoding" in {
        parse {
          """|HTTP/1.1 200 OK
             |Transfer-Encoding: chunked
             |
             |abc"""
        } mustEqual ErrorMessageParser("Non-identity transfer encodings are not currently supported", 501)
      }

      "with a header-value longer than 8192 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } mustEqual ErrorMessageParser("HTTP header values longer than 8192 characters are not supported (header 'Fancy')", 400)
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: 1.5
             |
             |abc"""
        } mustEqual ErrorMessageParser("Invalid Content-Length header value", 400)
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: -3
             |
             |abc"""
        } mustEqual ErrorMessageParser("Invalid Content-Length header value", 400)
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: 3
             |
             |abcde"""
        } mustEqual ErrorMessageParser("Illegal Content-Length: request entity is longer than Content-Length header value", 400)
      }
    }
  }

  def parse(response: String) = {
    val req = response.stripMargin.replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes("US-ASCII"))
    new EmptyResponseParser().read(buf) match {
      case CompleteMessageParser(StatusLine(protocol, status, reason), headers, connectionHeader, body) =>
        (protocol, status, reason, headers, connectionHeader, new String(body, "ISO-8859-1"))
      case x => x
    }
  }

}