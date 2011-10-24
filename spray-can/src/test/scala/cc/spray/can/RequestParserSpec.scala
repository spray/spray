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
        } mustEqual (GET, "/", `HTTP/1.1`, List(HttpHeader("Host", "example.com")), None, "")
      }

      "with 4 headers and a body" in {
        parse() {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Transfer-Encoding:identity
             |Connection:close
             |Content-Length    : 17
             |
             |Shake your BOODY!"""
        } mustEqual (POST, "/resource/yes", `HTTP/1.0`, List(
          HttpHeader("Content-Length", "17"),
          HttpHeader("Connection", "close"),
          HttpHeader("Transfer-Encoding", "identity"),
          HttpHeader("User-Agent", "curl/7.19.7 xyz")
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
          HttpHeader("Host", "example.com"),
          HttpHeader("Accept", "*/*"),
          HttpHeader("User-Agent", "curl/7.19.7 abc xyz")
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
        } mustEqual ChunkedStartParser(
          RequestLine(PUT, "/data", `HTTP/1.1`),
          List(HttpHeader("Host", "ping"), HttpHeader("Connection", "lalelu"), HttpHeader("Transfer-Encoding", "chunked")),
          Some("lalelu")
        )
      }
      "message chunk" in {
        def chunkParser = new ChunkParser(MessageParserConfig())
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
                ErrorParser("Invalid character '\\r', expected TOKEN CHAR, SPACE, TAB or EQUAL")
        parse(chunkParser)("bla") mustEqual ErrorParser("Illegal chunk size")
      }
      "message end" in {
        def chunkParser = new ChunkParser(MessageParserConfig())
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
          List(HttpHeader("Bar", "xyz"), HttpHeader("Foo", "pip apo"))
        )
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        parse()("get") mustEqual ErrorParser("Unsupported HTTP method", 501)
        parse()("GETX") mustEqual ErrorParser("Unsupported HTTP method", 501)
      }

      "an URI longer than 2048 chars" in {
        parse()("GET x" + "xxxx" * 512 + " HTTP/1.1") mustEqual
                ErrorParser("URIs with more than 2048 characters are not supported", 414)
      }

      "HTTP version 1.2" in {
        parse()("GET / HTTP/1.2\r\n") mustEqual ErrorParser("HTTP Version not supported", 505)
      }

      "with an illegal char in a header name" in {
        parse() {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } mustEqual ErrorParser("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse() {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } mustEqual ErrorParser("HTTP headers with names longer than 64 characters are not supported")
      }

      "with a header-value longer than 8192 chars" in {
        parse() {
          """|GET / HTTP/1.1
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } mustEqual ErrorParser("HTTP header values longer than 8192 characters are not supported (header 'Fancy')", 400)
      }

      "with an invalid Content-Length header value" in {
        parse() {
          """|GET / HTTP/1.0
             |Content-Length: 1.5
             |
             |abc"""
        } mustEqual ErrorParser("Invalid Content-Length header value: For input string: \"1.5\"", 400)
        parse() {
          """|GET / HTTP/1.0
             |Content-Length: -3
             |
             |abc"""
        } mustEqual ErrorParser("Invalid Content-Length header value: " +
                "requirement failed: Content-Length must not be negative", 400)
      }

      "a required Host header missing" in {
        parse() {
          """|GET / HTTP/1.1
             |
             |"""
        } mustEqual ErrorParser("Host header required", 400)
      }
    }
  }

  def parse(startParser: IntermediateParser = new EmptyRequestParser(MessageParserConfig())) = {
    RequestParserSpec.parse(startParser, extractFromCompleteMessage _) _
  }

  def extractFromCompleteMessage(completeMessage: CompleteMessageParser) = {
    val CompleteMessageParser(RequestLine(method, uri, protocol), headers, connectionHeader, body) = completeMessage
    (method, uri, protocol, headers, connectionHeader, new String(body, "ISO-8859-1"))
  }
}

object RequestParserSpec {
  def parse(startParser: => IntermediateParser,
            extractFromCompleteMessage: CompleteMessageParser => AnyRef)(response: String): AnyRef = {
    val req = response.stripMargin.replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes("US-ASCII"))
    startParser.read(buf) match {
      case x: CompleteMessageParser => extractFromCompleteMessage(x)
      case x: ToCloseBodyParser => extractFromCompleteMessage(x.complete)
      case ChunkedChunkParser(extensions, body) => (extensions, new String(body, "ISO-8859-1"))
      case ChunkedEndParser(extensions, trailer) => (extensions, trailer)
      case x => x
    }
  }
}