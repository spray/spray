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

package spray.can.parsing

import org.specs2.mutable.Specification
import com.typesafe.config.{ ConfigFactory, Config }
import scala.annotation.tailrec
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
    spray.can.parsing.max-uri-length = 20
    spray.can.parsing.max-content-length = 4000000000
    spray.can.parsing.incoming-auto-chunking-threshold-size = 20""")
  val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)
  val BOLT = HttpMethods.register(HttpMethod.custom("BOLT", safe = false, idempotent = true, entityAccepted = true))

  "The request parsing logic" should {
    "properly parse a request" in {
      "with no headers and no body" in {
        parse {
          """|GET / HTTP/1.0
             |
             |"""
        } === Seq(GET, Uri("/"), `HTTP/1.0`, Nil, "", 'close)
      }

      "with no headers and no body but remaining content" in {
        parse {
          """GET / HTTP/1.0
            |
            |POST /foo HTTP/1.0
            |
            |TRA""" // beginning of TRACE request
        } === Seq(GET, Uri("/"), `HTTP/1.0`, Nil, "", 'close, POST, Uri("/foo"), `HTTP/1.0`, Nil, "", 'close)
      }

      "with one header" in {
        parse {
          """|GET / HTTP/1.1
             |Host: example.com
             |
             |"""
        } === Seq(GET, Uri("/"), `HTTP/1.1`, List(Host("example.com")), "", 'dontClose)
      }

      "with 3 headers and a body" in {
        parse {
          """|POST /resource/yes HTTP/1.0
             |User-Agent: curl/7.19.7 xyz
             |Connection:keep-alive
             |Content-length:    17
             |
             |Shake your BOODY!"""
        } === Seq(POST, Uri("/resource/yes"), `HTTP/1.0`, List(`Content-Length`(17), Connection("keep-alive"),
          `User-Agent`("curl/7.19.7 xyz")), "Shake your BOODY!", 'dontClose)
      }

      "with 3 headers, a body and remaining content" in {
        parse {
          """POST /resource/yes HTTP/1.0
            |User-Agent: curl/7.19.7 xyz
            |Connection:keep-alive
            |Content-length:    17
            |
            |Shake your BOODY!GET / HTTP/1.0
            |
            |"""
        } === Seq(POST, Uri("/resource/yes"), `HTTP/1.0`, List(`Content-Length`(17), Connection("keep-alive"),
          `User-Agent`("curl/7.19.7 xyz")), "Shake your BOODY!", 'dontClose, GET, Uri("/"), `HTTP/1.0`, Nil, "", 'close)
      }

      "with multi-line headers" in {
        parse {
          """DELETE /abc HTTP/1.0
            |User-Agent: curl/7.19.7
            | abc
            |    xyz
            |Accept: */*
            |Connection: close,
            | fancy
            |Content-type: application/json
            |
            |"""
        } === Seq(DELETE, Uri("/abc"), `HTTP/1.0`, List(`Content-Type`(`application/json`), Connection("close", "fancy"),
          Accept(MediaRanges.`*/*`), `User-Agent`("curl/7.19.7 abc xyz")), "", 'close)
      }

      "byte-by-byte" in {
        val request = prep {
          """PUT /resource/yes HTTP/1.1
            |Content-length:    4
            |Host: x
            |
            |ABCDPATCH"""
        }
        rawParse(newParser)(request.toCharArray.map(_.toString)(collection.breakOut): _*) ===
          Seq(PUT, Uri("/resource/yes"), `HTTP/1.1`, List(Host("x"), `Content-Length`(4)), "ABCD", 'dontClose)
      }

      "with a custom HTTP method" in {
        parse {
          """BOLT / HTTP/1.0
            |
            |"""
        } === Seq(BOLT, Uri("/"), `HTTP/1.0`, Nil, "", 'close)
      }

      "with Content-Length > Int.MaxSize if autochunking is enabled" in {
        parse {
          """PUT /resource/yes HTTP/1.1
            |Content-length:    2147483649
            |Host: x
            |
            |"""
        } === Seq(PUT, Uri("/resource/yes"), `HTTP/1.1`, List(Host("x"), `Content-Length`(2147483649L)), 'dontClose)
      }
      "with several identical `Content-Type` headers" in {
        parse {
          """GET /data HTTP/1.1
            |Host: x
            |Content-Type: application/pdf
            |Content-Type: application/pdf
            |Content-Length: 0
            |
            |"""
        } === Seq(
          GET,
          Uri("/data"),
          `HTTP/1.1`,
          List(`Content-Length`(0), `Content-Type`(`application/pdf`), Host("x")),
          "", 'dontClose)
      }
    }

    "properly parse a chunked request" in {
      val start =
        """PATCH /data HTTP/1.1
          |Transfer-Encoding: chunked
          |Connection: lalelu
          |Content-Type: application/pdf
          |Host: ping
          |
          |"""
      val startMatch = Seq(PATCH, Uri("/data"), `HTTP/1.1`, List(Host("ping"),
        `Content-Type`(`application/pdf`), Connection("lalelu"), `Transfer-Encoding`("chunked")), 'dontClose)

      "request start" in {
        parse(start + "rest") === startMatch ++ Seq(400: StatusCode, "Illegal character 'r' in chunk start")
      }

      "message chunk with and without extension" in {
        parse(start,
          """3
            |abc
            |10;some=stuff;bla
            |0123456789ABCDEF
            |""",
          "10;foo=",
          """bar
            |0123456789ABCDEF
            |10
            |0123456789""",
          """ABCDEF
            |dead""") === startMatch ++
          Seq("abc", "", 'dontClose,
            "0123456789ABCDEF", "some=stuff;bla", 'dontClose,
            "0123456789ABCDEF", "foo=bar", 'dontClose,
            "0123456789ABCDEF", "", 'dontClose)
      }

      "message end" in {
        parse(start,
          """0
            |
            |""") === startMatch ++ Seq("", Nil, 'dontClose)
      }

      "message end with extension, trailer and remaining content" in {
        parse(start,
          """000;nice=true
            |Foo: pip
            | apo
            |Bar: xyz
            |
            |GE""") === startMatch ++
          Seq("nice=true", List(RawHeader("Bar", "xyz"), RawHeader("Foo", "pip apo")), 'dontClose)
      }
    }

    "properly auto-chunk" in {
      def start(contentSize: Int) =
        f"""GET /data HTTP/1.1
           |Content-Type: application/pdf
           |Host: ping
           |Content-Length: $contentSize%d
           |
           |"""
      def startMatch(contentSize: Int) =
        Seq(GET, Uri("/data"), `HTTP/1.1`, List(`Content-Length`(contentSize), Host("ping"), `Content-Type`(`application/pdf`)))

      "full request if size < incoming-auto-chunking-threshold-size" in {
        parse(start(1) + "r") === startMatch(1) ++ Seq("r", 'dontClose)
      }

      "request start" in {
        parse(start(25) + "rest") === startMatch(25) ++ Seq('dontClose,
          "rest", "", 'dontClose) // chunk
      }

      "request start if complete message is already available" in {
        parse(start(25) + "rest1rest2rest3rest4rest5") ===
          startMatch(25) ++ Seq('dontClose, // chunkstart
            "rest1rest2rest3rest4rest5", "", 'dontClose, // chunk
            "", Nil, 'dontClose) // chunk end
      }

      "request chunk" in {
        parse(start(25) + "rest1", "rest2") === startMatch(25) ++ Seq('dontClose, // chunkstart
          "rest1", "", 'dontClose, // chunk
          "rest2", "", 'dontClose) // chunk
      }

      "request end" in {
        parse(start(25) + "rest1", "rest2", "rest3", "rest4", "rest5GET /data HTTP") ===
          startMatch(25) ++ Seq('dontClose, // chunkstart
            "rest1", "", 'dontClose, // chunk
            "rest2", "", 'dontClose, // chunk
            "rest3", "", 'dontClose, // chunk
            "rest4", "", 'dontClose, // chunk
            "rest5", "", 'dontClose, // chunk
            "", Nil, 'dontClose) // chunk end
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
        parse(start,
          """15 ;
            |""").takeRight(2) === Seq(BadRequest, "Illegal character ' ' in chunk start")
      }

      "an illegal char in chunk size" in {
        parse(start, "bla").takeRight(2) === Seq(BadRequest, "Illegal character 'l' in chunk start")
      }

      "too-long chunk extension" in {
        parse(start, "3;" + ("x" * 257)).takeRight(2) ===
          Seq(BadRequest, "HTTP chunk extension length exceeds configured limit of 256 characters")
      }

      "too-large chunk size" in {
        parse(start,
          """1a2b3c4d5e
            |""").takeRight(2) === Seq(BadRequest, "HTTP chunk size exceeds the configured limit of 1048576 bytes")
      }

      "an illegal chunk termination" in {
        parse(start,
          """3
            |abcde""").takeRight(2) === Seq(BadRequest, "Illegal chunk termination")
      }

      "an illegal header in the trailer" in {
        parse(start,
          """0
            |F@oo: pip""").takeRight(2) === Seq(BadRequest, "Illegal character '@' in header name")
      }
    }

    "reject a request with" in {
      "an illegal HTTP method" in {
        parse("get ") === Seq(NotImplemented, "Unsupported HTTP method: get")
        parse("GETX ") === Seq(NotImplemented, "Unsupported HTTP method: GETX")
      }

      "two Content-Length headers" in {
        parse {
          """GET / HTTP/1.1
            |Content-Length: 3
            |Content-Length: 3
            |
            |foo"""
        } === Seq(BadRequest, "HTTP message must not contain more than one Content-Length header")
      }

      "a too-long URI" in {
        parse("GET /23456789012345678901 HTTP/1.1") ===
          Seq(RequestUriTooLong, "URI length exceeds the configured limit of 20 characters")
      }

      "HTTP version 1.2" in {
        parse {
          """GET / HTTP/1.2
            |"""
        } ===
          Seq(HTTPVersionNotSupported, "The server does not support the HTTP protocol version used in the request.")
      }

      "with an illegal char in a header name" in {
        parse {
          """|GET / HTTP/1.1
             |User@Agent: curl/7.19.7"""
        } === Seq(BadRequest, "Illegal character '@' in header name")
      }

      "with a too-long header name" in {
        parse {
          """|GET / HTTP/1.1
             |UserxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxAgent: curl/7.19.7"""
        } === Seq(BadRequest, "HTTP header name exceeds the configured limit of 64 characters")
      }

      "with a too-long header-value" in {
        parse {
          """|GET / HTTP/1.1
             |Fancy: 123456789012345678901234567890123"""
        } === Seq(BadRequest, "HTTP header value exceeds the configured limit of 32 characters")
      }

      "with an invalid Content-Length header value" in {
        parse {
          """|GET / HTTP/1.0
             |Content-Length: 1.5
             |
             |abc"""
        } === Seq(BadRequest, "Illegal `Content-Length` header value")
      }

      "with Content-Length > Int.MaxSize if autochunking is disabled" in {
        val request =
          """PUT /resource/yes HTTP/1.1
            |Content-length:    2147483649
            |Host: x
            |
            |"""
        val parser = new HttpRequestPartParser(ParserSettings(system).copy(autoChunkingThreshold = Long.MaxValue))()
        parse(parser)(request) === Seq(400: StatusCode, "Content-Length > Int.MaxSize not supported for non-(auto)-chunked requests")
      }

      "with Content-Length > Long.MaxSize" in {
        // content-length = (Long.MaxValue + 1) * 10, which is 0 when calculated overflow
        parse {
          """PUT /resource/yes HTTP/1.1
            |Content-length: 92233720368547758080
            |Host: x
            |
            |"""
        } === Seq(400: StatusCode, "Illegal `Content-Length` header value")
      }
    }
  }

  step(system.shutdown())

  def newParser = new HttpRequestPartParser(ParserSettings(system))()

  def parse(rawRequest: String*): Seq[Any] = parse(newParser)(rawRequest: _*)

  def parse(parser: Parser)(rawRequest: String*): Seq[Any] = rawParse(parser)(rawRequest map prep: _*)

  def rawParse(parser: Parser)(rawRequest: String*): Seq[Any] = {
    def closeSymbol(close: Boolean) = if (close) 'close else 'dontClose
    @tailrec def rec(current: Result, remainingData: List[ByteString], result: Seq[Any] = Seq.empty): Seq[Any] =
      current match {
        case Result.Emit(HttpRequest(m, u, h, e, p), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(m, u, p, h, e.asString, closeSymbol(c)))
        case Result.Emit(ChunkedRequestStart(HttpRequest(m, u, h, HttpEntity.Empty, p)), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(m, u, p, h, closeSymbol(c)))
        case Result.Emit(MessageChunk(d, ext), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(d.asString, ext, closeSymbol(c)))
        case Result.Emit(ChunkedMessageEnd(ext, trailer), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(ext, trailer, closeSymbol(c)))
        case Result.NeedMoreData(p) ⇒
          if (remainingData.nonEmpty) rec(p(remainingData.head), remainingData.tail, result) else result
        case Result.ParsingError(status, info) ⇒ result ++ Seq(status, info.formatPretty)
        case x                                 ⇒ Seq(x)
      }

    val data: List[ByteString] = rawRequest.map(ByteString.apply)(collection.breakOut)
    rec(parser(data.head), data.tail)
  }

  def prep(response: String) = response.stripMarginWithNewline("\r\n")
}
