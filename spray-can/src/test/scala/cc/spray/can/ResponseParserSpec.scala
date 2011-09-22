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
import HttpProtocols._
import HttpMethods._

class ResponseParserSpec extends Specification {

  "The reponse parsing logic" should {
    "properly parse a response" in {
      "without headers and body" in {
        parse {
          """|HTTP/1.1 200 OK
             |
             |"""
        } mustEqual ErrorParser("Content-Length header or chunked transfer encoding required", 411)
      }

      "with one header, a body, but no Content-Length header" in {
        parse {
          """|HTTP/1.0 404 Not Found
             |Host: api.example.com
             |
             |Foobs"""
        } mustEqual (`HTTP/1.0`, 404, "Not Found", List(HttpHeader("Host", "api.example.com")), None, "Foobs")
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
          """|HTTP/1.0 200 OK
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |
             |"""
        } mustEqual (`HTTP/1.0`, 200, "OK", List(
          HttpHeader("Accept", "*/*"),
          HttpHeader("User-Agent", "curl/7.19.7 abc xyz")
        ), None, "")
      }
    }

    "properly parse a" in {
      "chunked response start" in {
        parse {
          """|HTTP/1.1 200 OK
             |User-Agent: curl/7.19.7
             |Transfer-Encoding: chunked
             |
             |3
             |abc"""
        } mustEqual ChunkedStartParser(
          StatusLine(GET, `HTTP/1.1`, 200, "OK"),
          List(HttpHeader("Transfer-Encoding", "chunked"), HttpHeader("User-Agent", "curl/7.19.7")),
          None
        )
      }
    }

    "reject a response with" in {
      "HTTP version 1.2" in {
        parse("HTTP/1.2 200 OK\r\n") mustEqual ErrorParser("HTTP Version not supported", 505)
      }

      "an illegal status code" in {
        parse("HTTP/1.1 700 Something") mustEqual ErrorParser("Illegal response status code")
        parse("HTTP/1.1 2000 Something") mustEqual ErrorParser("Illegal response status code")
      }

      "a response status reason longer than 64 chars" in {
        parse("HTTP/1.1 250 x" + "xxxx" * 16 + "\r\n") mustEqual
                ErrorParser("Reason phrases with more than 64 characters are not supported")
      }

      "with an illegal char in a header name" in {
        parse {
          """|HTTP/1.1 200 OK
             |User@Agent: curl/7.19.7"""
        } mustEqual ErrorParser("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } mustEqual ErrorParser("HTTP headers with names longer than 64 characters are not supported")
      }

      "with a header-value longer than 8192 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } mustEqual ErrorParser("HTTP header values longer than 8192 characters are not supported (header 'Fancy')", 400)
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: 1.5
             |
             |abc"""
        } mustEqual ErrorParser("Invalid Content-Length header value: For input string: \"1.5\"", 400)
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: -3
             |
             |abc"""
        } mustEqual ErrorParser("Invalid Content-Length header value: " +
                "requirement failed: Content-Length must not be negative", 400)
      }
    }
  }

  def parse = RequestParserSpec.parse(new EmptyResponseParser(MessageParserConfig(), HttpMethods.GET), extractFromCompleteMessage _) _

  def extractFromCompleteMessage(completeMessage: CompleteMessageParser) = {
    val CompleteMessageParser(StatusLine(_, protocol, status, reason), headers, connectionHeader, body) = completeMessage
    (protocol, status, reason, headers, connectionHeader, new String(body, "ISO-8859-1"))
  }

}