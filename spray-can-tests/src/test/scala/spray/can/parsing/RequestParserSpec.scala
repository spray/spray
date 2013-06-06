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

package spray.can.parsing

import org.specs2.mutable.Specification
import com.typesafe.config.{ ConfigFactory, Config }
import akka.util.ByteString
import akka.actor.ActorSystem
import spray.util._
import spray.http._
import MediaTypes._
import HttpMethods._
import HttpHeaders._
import HttpProtocols._
import StatusCodes._

class RequestParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    spray.can.parsing.max-header-value-length = 32
    spray.can.parsing.max-uri-length = 20""")
  val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)

  "The request parsing logic" should {
    "properly parse a request" in {
      "with no headers and no body" in {
        parse {
          """|GET / HTTP/1.0
             |
             |"""
        } === (GET, Uri("/"), `HTTP/1.0`, Nil, "", "", true)
      }

      "with no headers and no body but remaining content" in {
        parse {
          """|GET / HTTP/1.0
             |
             |This is not part of the request!"""
        } === (GET, Uri("/"), `HTTP/1.0`, Nil, "", "This is not part of the request!", true)
      }

      "with one header" in {
        parse {
          """|GET / HTTP/1.1
             |Host: example.com
             |
             |"""
        } === (GET, Uri("/"), `HTTP/1.1`, List(Host("example.com")), "", "", false)
      }

      "with 3 headers and a body" in {
        parse {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Connection:keep-alive
             |Content-length:    17
             |
             |Shake your BOODY!"""
        } === (POST, Uri("/resource/yes"), `HTTP/1.0`, List(`Content-Length`(17), Connection("keep-alive"),
          `User-Agent`("curl/7.19.7 xyz")), "Shake your BOODY!", "", false)
      }

      "with 3 headers, a body and remaining content" in {
        parse {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Connection:keep-alive
             |Content-length:    17
             |
             |Shake your BOODY!Non-Body-Text"""
        } === (POST, Uri("/resource/yes"), `HTTP/1.0`, List(`Content-Length`(17), Connection("keep-alive"),
          `User-Agent`("curl/7.19.7 xyz")), "Shake your BOODY!", "Non-Body-Text", false)
      }

      "with multi-line headers" in {
        parse {
          """DELETE /abc HTTP/1.1
            |User-Agent: curl/7.19.7
            | abc
            |    xyz
            |Accept: */*  """ + '\n' +
            """Connection: close,
            | fancy
            |Content-type: application/json
            |
            |"""
        } === (DELETE, Uri("/abc"), `HTTP/1.1`, List(`Content-Type`(`application/json`), Connection("close", "fancy"),
          Accept(MediaRanges.`*/*`), `User-Agent`("curl/7.19.7 abc xyz")), "", "", true)
      }
    }

    "properly parse a chunked" in {
      val start =
        """PATCH /data HTTP/1.1
          |Transfer-Encoding: chunked
          |Connection: lalelu
          |Content-Type: application/pdf
          |Host: ping
          |
          |"""

      "request start" in {
        parse(start + "rest") === (PATCH, Uri("/data"), `HTTP/1.1`, List(Host("ping"), `Content-Type`(`application/pdf`),
          Connection("lalelu"), `Transfer-Encoding`("chunked")), "rest", false)
      }

      "message chunk with and without extension" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("3\nabc\n") === ("abc", "", "", false)
        parse(parser)("10;some=stuff;bla\n0123456789ABCDEF\n") === ("0123456789ABCDEF", "some=stuff;bla", "", false)
        parse(parser)("10;foo=") === Result.NeedMoreData
        parse(parser)("bar\n0123456789ABCDEF\nmore") === ("0123456789ABCDEF", "foo=bar", "more", false)
        parse(parser)("10\n0123456789") === Result.NeedMoreData
        parse(parser)("ABCDEF\neven-more") === ("0123456789ABCDEF", "", "even-more", false)
      }

      "message end" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("0\n\n") === ("", Nil, "", false)
      }

      "message end with extension, trailer and remaining content" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser) {
          """000;nice=true
            |Foo: pip
            | apo
            |Bar: xyz
            |
            |rest"""
        } === ("nice=true", List(RawHeader("Bar", "xyz"), RawHeader("Foo", "pip apo")), "rest", false)
      }
    }

    "reject a message chunk with" in {
      val start =
        """PATCH /data HTTP/1.1
          |Transfer-Encoding: chunked
          |Connection: lalelu
          |Host: ping
          |
          |"""

      "an illegal char after chunk size" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("15 ;\n") === (BadRequest, "Illegal character ' ' in chunk start")
      }

      "an illegal char in chunk size" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("bla") === (BadRequest, "Illegal character 'l' in chunk start")
      }

      "too-long chunk extension" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("3;" + ("x" * 257)) === (BadRequest, "HTTP chunk extension length exceeds configured limit of 256 characters")
      }

      "too-large chunk size" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("1a2b3c4d5e\n") === (BadRequest, "HTTP chunk size exceeds the configured limit of 1048576 bytes")
      }

      "an illegal chunk termination" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("3\nabcde") === (BadRequest, "Illegal chunk termination")
      }

      "an illegal header in the trailer" in {
        val parser = newParser
        parse(parser)(start)
        parse(parser)("0\nF@oo: pip") === (BadRequest, "Illegal character '@' in header name")
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        parse("get") === (NotImplemented, "Unsupported HTTP method")
        parse("GETX") === (NotImplemented, "Unsupported HTTP method")
      }

      "two Content-Length headers" in {
        parse {
          """GET / HTTP/1.1
            |Content-Length: 3
            |Content-Length: 3
            |
            |foo"""
        } === (BadRequest, "HTTP message must not contain more than one Content-Length header")
      }

      "a too-long URI" in {
        parse("GET /23456789012345678901 HTTP/1.1") ===
          (RequestUriTooLong, "URI length exceeds the configured limit of 20 characters")
      }

      "HTTP version 1.2" in {
        parse("GET / HTTP/1.2\r\n") === (HTTPVersionNotSupported, "The server does not support the HTTP protocol version used in the request.")
      }

      "with an illegal char in a header name" in {
        parse {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } === (BadRequest, "Illegal character '@' in header name")
      }

      "with a too-long header name" in {
        parse {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } === (BadRequest, "HTTP header name exceeds the configured limit of 64 characters")
      }

      "with a too-long header-value" in {
        parse {
          """|GET / HTTP/1.1
             |Fancy: 123456789012345678901234567890123"""
        } === (BadRequest, "HTTP header value exceeds the configured limit of 32 characters")
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|GET / HTTP/1.0
             |Content-Length: 1.5
             |
             |abc"""
        } === (BadRequest, "Illegal `Content-Length` header value")
      }
    }
  }

  step(system.shutdown())

  def newParser = new HttpRequestPartParser(ParserSettings(system))()

  def parse(rawRequest: String): AnyRef = parse(newParser)(rawRequest)

  def parse(parser: HttpRequestPartParser)(rawRequest: String): AnyRef = {
    val data = ByteString(rawRequest.stripMargin.replace(EOL, "\n").replace("\n", "\r\n"))
    parser.parse(data) match {
      case Result.Ok(HttpRequest(m, u, h, e, p), rd, close) ⇒ (m, u, p, h, e.asString, rd.utf8String, close)
      case Result.Ok(ChunkedRequestStart(HttpRequest(m, u, h, EmptyEntity, p)), rd, close) ⇒ (m, u, p, h, rd.utf8String, close)
      case Result.Ok(MessageChunk(body, ext), rd, close) ⇒ (new String(body), ext, rd.utf8String, close)
      case Result.Ok(ChunkedMessageEnd(ext, trailer), rd, close) ⇒ (ext, trailer, rd.utf8String, close)
      case Result.ParsingError(status, info) ⇒ (status, info.formatPretty)
      case x ⇒ x
    }
  }
}
