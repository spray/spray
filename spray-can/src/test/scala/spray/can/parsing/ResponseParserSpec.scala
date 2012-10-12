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

package spray.can.parsing

import org.specs2.mutable.Specification
import spray.can.StatusLine
import spray.util._
import spray.http._
import HttpHeaders._
import HttpProtocols._


class ResponseParserSpec extends Specification {

  "The reponse parsing logic" should {
    "properly parse a response" in {
      "without headers and body" in {
        parse {
          """|HTTP/1.1 200 OK
             |
             |"""
        } === ErrorState(StatusCodes.LengthRequired, "Content-Length header or chunked transfer encoding required")
      }

      "with one header, a body, but no Content-Length header" in {
        parse {
          """|HTTP/1.0 404 Not Found
             |Host: api.example.com
             |
             |Foobs"""
        } === (`HTTP/1.0`, 404, "Not Found", List(RawHeader("host", "api.example.com")), None, None, "Foobs")
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
        } === (`HTTP/1.1`, 500, "Internal Server Error", List(
          RawHeader("content-length", "17"),
          RawHeader("connection", "close"),
          RawHeader("transfer-encoding", "identity"),
          RawHeader("user-agent", "curl/7.19.7 xyz")
        ), Some("close"), None, "Shake your BOODY!")
      }

      "with multi-line headers" in {
        parse {
          """|HTTP/1.0 200 OK
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |Content-type: application/json
             |
             |"""
        } === (`HTTP/1.0`, 200, "OK", List(
          RawHeader("content-type", "application/json"),
          RawHeader("accept", "*/*"),
          RawHeader("user-agent", "curl/7.19.7 abc xyz")
        ), None, Some(ContentType(MediaTypes.`application/json`)), "")
      }

      "to a HEAD request" in {
        RequestParserSpec.parse(new EmptyResponseParser(new ParserSettings(), true), identityFunc) {
          """|HTTP/1.1 500 Internal Server Error
             |Content-Length: 17
             |
             |"""
        } === CompleteMessageState(
          messageLine = StatusLine(`HTTP/1.1`, 500, "Internal Server Error", true),
          headers = List(RawHeader("content-length", "17"))
        )
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
        } === ChunkedStartState(
          StatusLine(`HTTP/1.1`, 200, "OK"),
          List(RawHeader("transfer-encoding", "chunked"), RawHeader("user-agent", "curl/7.19.7")),
          None
        )
      }
    }

    "reject a response with" in {
      "HTTP version 1.2" in {
        parse("HTTP/1.2 200 OK\r\n") === ErrorState(StatusCodes.HTTPVersionNotSupported)
      }

      "an illegal status code" in {
        parse("HTTP/1.1 700 Something") === ErrorState("Illegal response status code")
        parse("HTTP/1.1 2000 Something") === ErrorState("Illegal response status code")
      }

      "a response status reason longer than 64 chars" in {
        parse("HTTP/1.1 250 x" + "xxxx" * 16 + "\r\n") ===
                ErrorState("Reason phrase exceeds the configured limit of 64 characters")
      }

      "with an illegal char in a header name" in {
        parse {
          """|HTTP/1.1 200 OK
             |User@Agent: curl/7.19.7"""
        } === ErrorState("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } === ErrorState("HTTP header name exceeds the configured limit of 64 characters",
          "header 'userxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx...'")
      }

      "with a header-value longer than 8192 chars" in {
        parse {
          """|HTTP/1.1 200 OK
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } === ErrorState("HTTP header value exceeds the configured limit of 8192 characters", "header 'fancy'")
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: 1.5
             |
             |abc"""
        } === ErrorState("Invalid Content-Length header value: For input string: \"1.5\"")
        parse {
          """|HTTP/1.1 200 OK
             |Content-Length: -3
             |
             |abc"""
        } === ErrorState("Invalid Content-Length header value: " +
                "requirement failed: Content-Length must not be negative")
      }
    }
  }

  def parse =
    RequestParserSpec.parse(new EmptyResponseParser(new ParserSettings(), false), extractFromCompleteMessage _) _

  def extractFromCompleteMessage(completeMessage: CompleteMessageState) = {
    import completeMessage._
    val StatusLine(protocol, status, reason, _) = messageLine
    (protocol, status, reason, headers, connectionHeader, contentType, body.asString("ISO-8859-1"))
  }

}
