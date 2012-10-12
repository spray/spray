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

import java.nio.ByteBuffer
import org.specs2.mutable.Specification
import spray.can.RequestLine
import spray.util._
import spray.http._
import HttpHeaders._
import HttpMethods._
import HttpProtocols._
import StatusCodes._


class RequestParserSpec extends Specification {

  "The request parsing logic" should {
    "properly parse a request" in {
      "without headers and body" in {
        parse {
          """|GET / HTTP/1.0
             |
             |"""
        } === (GET, "/", `HTTP/1.0`, Nil, None, None, "")
      }

      "with one header" in {
        parse {
          """|GET / HTTP/1.1
             |Host: example.com
             |
             |"""
        } === (GET, "/", `HTTP/1.1`, List(RawHeader("host", "example.com")), None, None, "")
      }

      "with 4 headers and a body" in {
        parse {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Transfer-Encoding:identity
             |Connection:close
             |Content-length    : 17
             |
             |Shake your BOODY!"""
        } === (POST, "/resource/yes", `HTTP/1.0`, List(
          RawHeader("content-length", "17"),
          RawHeader("connection", "close"),
          RawHeader("transfer-encoding", "identity"),
          RawHeader("user-agent", "curl/7.19.7 xyz")
        ), Some("close"), None, "Shake your BOODY!")
      }

      "with multi-line headers" in {
        parse {
          """|DELETE /abc HTTP/1.1
             |User-Agent: curl/7.19.7
             | abc
             |    xyz
             |Accept
             | : */*  """ + """
             |Host: example.com
             |Content-type: application/json
             |
             |"""
        } === (DELETE, "/abc", `HTTP/1.1`, List(
          RawHeader("content-type", "application/json"),
          RawHeader("host", "example.com"),
          RawHeader("accept", "*/*"),
          RawHeader("user-agent", "curl/7.19.7 abc xyz")
        ), None, Some(ContentType(MediaTypes.`application/json`)), "")
      }
    }

    "properly parse a chunked" in {
      "request start" in {
        parse {
          """|PATCH /data HTTP/1.1
             |Transfer-Encoding: chunked
             |Connection: lalelu
             |Host: ping
             |
             |3
             |abc
             |"""
        } === ChunkedStartState(
          RequestLine(PATCH, "/data", `HTTP/1.1`),
          List(
            RawHeader("host", "ping"),
            RawHeader("connection", "lalelu"),
            RawHeader("transfer-encoding", "chunked")
          ),
          Some("lalelu")
        )
      }
      "message chunk" in {
        def chunkParser = new ChunkParser(new ParserSettings())
        parse(chunkParser)("3\nabc\n") === (Nil, "abc")
        parse(chunkParser)("10 ;key= value ; another=one;and =more \n0123456789ABCDEF\n") === (
          List(
            ChunkExtension("and", "more"),
            ChunkExtension("another", "one"),
            ChunkExtension("key", "value")
          ),
          "0123456789ABCDEF"
        )
        parse(chunkParser)("15 ;\n") ===
          ErrorState("Invalid character '\\u000d', expected TOKEN CHAR, SPACE, TAB or EQUAL")
        parse(chunkParser)("bla") === ErrorState("Illegal chunk size")
      }
      "message end" in {
        def chunkParser = new ChunkParser(new ParserSettings())
        parse(chunkParser)("0\n\n") === (Nil, Nil)
        parse(chunkParser) {
          """|000;nice=true
             |Foo: pip
             | apo
             |Bar: xyz
             |
             |"""
        } === (
          List(ChunkExtension("nice", "true")),
          List(RawHeader("bar", "xyz"), RawHeader("foo", "pip apo"))
        )
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        parse("get") === ErrorState(NotImplemented)
        parse("GETX") === ErrorState(NotImplemented)
      }

      "an URI longer than 2048 chars" in {
        parse("GET x" + "xxxx" * 512 + " HTTP/1.1") ===
                ErrorState(RequestUriTooLong, "URI length exceeds the configured limit of 2048 characters")
      }

      "HTTP version 1.2" in {
        parse("GET / HTTP/1.2\r\n") === ErrorState(HTTPVersionNotSupported)
      }

      "with an illegal char in a header name" in {
        parse {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } === ErrorState("Invalid character '@', expected TOKEN CHAR, LWS or COLON")
      }

      "with a header name longer than 64 chars" in {
        parse {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } === ErrorState("HTTP header name exceeds the configured limit of 64 characters",
          "header 'userxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx...'")
      }

      "with a header-value longer than 8192 chars" in {
        parse {
          """|GET / HTTP/1.1
             |Fancy: 0""" + ("12345678" * 1024) + "\r\n"
        } === ErrorState("HTTP header value exceeds the configured limit of 8192 characters", "header 'fancy'")
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|GET / HTTP/1.0
             |Content-Length: 1.5
             |
             |abc"""
        } === ErrorState("Invalid Content-Length header value: For input string: \"1.5\"")
        parse {
          """|GET / HTTP/1.0
             |Content-Length: -3
             |
             |abc"""
        } === ErrorState("Invalid Content-Length header value: " +
                "requirement failed: Content-Length must not be negative")
      }

      "a required Host header missing" in {
        parse {
          """|GET / HTTP/1.1
             |
             |"""
        } === ErrorState("Host header required")
      }
    }
  }

  def parse: String => AnyRef = parse(new EmptyRequestParser(new ParserSettings()))
  
  def parse(startParser: IntermediateState): String => AnyRef = {
    RequestParserSpec.parse(startParser, extractFromCompleteMessage _) _
  }

  def extractFromCompleteMessage(completeMessage: CompleteMessageState) = {
    import completeMessage._
    val RequestLine(method, uri, protocol) = messageLine
    (method, uri, protocol, headers, connectionHeader, contentType, body.asString("ISO-8859-1"))
  }
}

object RequestParserSpec {
  def parse(startParser: => IntermediateState,
            extractFromCompleteMessage: CompleteMessageState => AnyRef)(response: String): AnyRef = {
    // Some tests use multiline strings and some use one line with "\n" separators
    val req = response.stripMargin.replace(EOL, "\n").replace("\n", "\r\n")
    val buf = ByteBuffer.wrap(req.getBytes("US-ASCII"))
    startParser.read(buf) match {
      case x: CompleteMessageState => extractFromCompleteMessage(x)
      case x: ToCloseBodyParser => extractFromCompleteMessage(x.complete)
      case ChunkedChunkState(extensions, body) => (extensions, body.asString("ISO-8859-1"))
      case ChunkedEndState(extensions, trailer) => (extensions, trailer)
      case x => x
    }
  }
}
