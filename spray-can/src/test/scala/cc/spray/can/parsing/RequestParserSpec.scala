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
package parsing

import org.specs2.mutable.Specification
import java.nio.ByteBuffer
import model._
import HttpMethods._
import HttpProtocols._

class RequestParserSpec extends Specification {

  "The request parsing logic" should {
    "properly parse a request" in {
      "without headers and body" in {
        parse() {
          """|GET / HTTP/1.0
             |
             |"""
        } mustEqual (GET, "/", `HTTP/1.0`, Nil, None, "")
      }

      "with one header" in {
        parse() {
          """|GET / HTTP/1.1
             |Host: example.com
             |
             |"""
        } mustEqual (GET, "/", `HTTP/1.1`, List(HttpHeader("host", "example.com")), None, "")
      }

      "with 4 headers and a body" in {
        parse() {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Transfer-Encoding:identity
             |Connection:close
             |Content-length    : 17
             |
             |Shake your BOODY!"""
        } mustEqual (POST, "/resource/yes", `HTTP/1.0`, List(
          HttpHeader("content-length", "17"),
          HttpHeader("connection", "close"),
          HttpHeader("transfer-encoding", "identity"),
          HttpHeader("user-agent", "curl/7.19.7 xyz")
        ), Some("close"), "Shake your BOODY!")
      }

      "with multi-line headers" in {
        parse() {
          """|DELETE /abc HTTP/1.1
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |Host: example.com
             |
             |"""
        } mustEqual (DELETE, "/abc", `HTTP/1.1`, List(
          HttpHeader("host", "example.com"),
          HttpHeader("accept", "*/*"),
          HttpHeader("user-agent", "curl/7.19.7 abc xyz")
        ), None, "")
      }
    }

    "properly parse a chunked" in {
      "request start" in {
        parse() {
          """|PUT /data HTTP/1.1
             |Transfer-Encoding: chunked
             |Connection: lalelu
             |Host: ping
             |
             |3
             |abc
             |"""
        } mustEqual ChunkedStartState(
          RequestLine(PUT, "/data", `HTTP/1.1`),
          List(HttpHeader("host", "ping"), HttpHeader("connection", "lalelu"), HttpHeader("transfer-encoding", "chunked")),
          Some("lalelu")
        )
      }
      "message chunk" in {
        def chunkParser = new ChunkParser(new ParserSettings())
        parse(chunkParser)("3\nabc\n") mustEqual (Nil, "abc")
        parse(chunkParser)("10 ;key= value ; another=one;and =more \n0123456789ABCDEF\n") mustEqual(
          List(
            ChunkExtension("and", "more"),
            ChunkExtension("another", "one"),
            ChunkExtension("key", "value")
          ),
          "0123456789ABCDEF"
        )
        parse(chunkParser)("15 ;\n") mustEqual
          ErrorState("Invalid character '\\r', expected TOKEN CHAR, SPACE, TAB or EQUAL")
        parse(chunkParser)("bla") mustEqual ErrorState("Illegal chunk size")
      }
      "message end" in {
        def chunkParser = new ChunkParser(new ParserSettings())
        parse(chunkParser)("0\n\n") mustEqual (Nil, Nil)
        parse(chunkParser) {
          """|000;nice=true
             |Foo: pip
             | apo
             |Bar: xyz
             |
             |"""
        } mustEqual (
          List(ChunkExtension("nice", "true")),
          List(HttpHeader("bar", "xyz"), HttpHeader("foo", "pip apo"))
        )
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        parse()("get") mustEqual ErrorState("Unsupported HTTP method", 501)
        parse()("GETX") mustEqual ErrorState("Unsupported HTTP method", 501)
      }

      "an URI longer than 2048 chars" in {
        parse()("GET x" + "xxxx" * 512 + " HTTP/1.1") mustEqual
                ErrorState("URI length exceeds the configured limit of 2048 characters", 414)
      }

      "HTTP version 1.2" in {
        parse()("GET / HTTP/1.2\r\n") mustEqual ErrorState("HTTP Version not supported", 505)
      }

      "with an illegal char in a header name" in {
        parse() {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } mustEqual ErrorState("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse() {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } mustEqual ErrorState("HTTP header name exceeds the configured limit of 64 characters (userxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx...)")
      }

      "with a header-value longer than 8192 chars" in {
        parse() {
          """|GET / HTTP/1.1
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } mustEqual ErrorState("HTTP header value exceeds the configured limit of 8192 characters (header 'fancy')", 400)
      }

      "with an invalid Content-Length header value" in {
        parse() {
          """|GET / HTTP/1.0
             |Content-Length: 1.5
             |
             |abc"""
        } mustEqual ErrorState("Invalid Content-Length header value: For input string: \"1.5\"", 400)
        parse() {
          """|GET / HTTP/1.0
             |Content-Length: -3
             |
             |abc"""
        } mustEqual ErrorState("Invalid Content-Length header value: " +
                "requirement failed: Content-Length must not be negative", 400)
      }

      "a required Host header missing" in {
        parse() {
          """|GET / HTTP/1.1
             |
             |"""
        } mustEqual ErrorState("Host header required", 400)
      }
    }
  }

  def parse(startParser: IntermediateState = new EmptyRequestParser(new ParserSettings())) = {
    RequestParserSpec.parse(startParser, extractFromCompleteMessage _) _
  }

  def extractFromCompleteMessage(completeMessage: CompleteMessageState) = {
    val CompleteMessageState(RequestLine(method, uri, protocol), headers, connectionHeader, body) = completeMessage
    (method, uri, protocol, headers, connectionHeader, new String(body, "ISO-8859-1"))
  }
}

object RequestParserSpec {
  def parse(startParser: => IntermediateState,
            extractFromCompleteMessage: CompleteMessageState => AnyRef)(response: String): AnyRef = {
    val req = response.stripMargin.replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes("US-ASCII"))
    startParser.read(buf) match {
      case x: CompleteMessageState => extractFromCompleteMessage(x)
      case x: ToCloseBodyParser => extractFromCompleteMessage(x.complete)
      case ChunkedChunkState(extensions, body) => (extensions, new String(body, "ISO-8859-1"))
      case ChunkedEndState(extensions, trailer) => (extensions, trailer)
      case x => x
    }
  }
}
