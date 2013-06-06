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

import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import akka.util.ByteString
import spray.util._
import spray.http._
import HttpHeaders._
import HttpMethods._
import StatusCodes._
import HttpProtocols._

class ResponseParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    spray.can.parsing.max-response-reason-length = 21""")
  val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)

  "The response parsing logic" should {
    "properly parse" in {

      // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3.3 (1)
      "a 200 response to a HEAD request" in {
        parse(
          """HTTP/1.1 200 OK
            |
            |rest""",
          requestMethod = HEAD) === (OK, "", Nil, `HTTP/1.1`, "rest", false)
      }

      // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3.3 (1)
      "a 204 response" in {
        parse {
          """HTTP/1.1 204 OK
            |
            |rest"""
        } === (NoContent, "", Nil, `HTTP/1.1`, "rest", false)
      }

      "a response with one header, a body, but no Content-Length header" in {
        val parser = newParser()
        parse(parser) {
          """HTTP/1.0 404 Not Found
            |Host: api.example.com
            |
            |Foobs"""
        } === Result.NeedMoreData
        parse(parser)("") === (NotFound, "Foobs", List(Host("api.example.com")), `HTTP/1.0`, "", true)
      }

      "a response with 3 headers, a body and remaining content" in {
        parse {
          """HTTP/1.1 500 Internal Server Error
            |User-Agent: curl/7.19.7 xyz
            |Connection:close
            |Content-Length: 17
            |
            |Shake your BOODY!XXX"""
        } === (InternalServerError, "Shake your BOODY!", List(`Content-Length`(17), Connection("close"),
          `User-Agent`("curl/7.19.7 xyz")), `HTTP/1.1`, "XXX", true)
      }
    }

    "properly parse a chunked" in {
      val start =
        """HTTP/1.1 200 OK
          |Transfer-Encoding: chunked
          |Server: spray-can
          |
          |"""

      "response start" in {
        parse(start + "rest") ===
          (OK, List(Server("spray-can"), `Transfer-Encoding`("chunked")), `HTTP/1.1`, "rest", false)
      }

      "message chunk with and without extension" in {
        val parser = newParser()
        parse(parser)(start)
        parse(parser)("3\nabc\n") === ("abc", "", "", false)
        parse(parser)("10;some=stuff;bla\n0123456789ABCDEF\n") === ("0123456789ABCDEF", "some=stuff;bla", "", false)
      }

      "message end" in {
        val parser = newParser()
        parse(parser)(start)
        parse(parser)("0\n\n") === ("", Nil, "", false)
      }

      "message end with extension, trailer and remaining content" in {
        val parser = newParser()
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

    "reject a response with" in {
      "HTTP version 1.2" in {
        parse("HTTP/1.2 200 OK\r\n") === "The server-side HTTP version is not supported"
      }

      "an illegal status code" in {
        parse("HTTP/1.1 700 Something") === "Illegal response status code"
        parse("HTTP/1.1 2000 Something") === "Illegal response status code"
      }

      "a too-long response status reason" in {
        parse("HTTP/1.1 204 1234567890123456789012\r\n") ===
          "Response reason phrase exceeds the configured limit of 21 characters"
      }
    }
  }

  step(system.shutdown())

  def newParser(requestMethod: HttpMethod = GET) = {
    val parser = new HttpResponsePartParser(ParserSettings(system))()
    parser.startResponse(requestMethod)
    parser
  }

  def parse(rawResponse: String, requestMethod: HttpMethod = GET): AnyRef =
    parse(newParser(requestMethod))(rawResponse)

  def parse(parser: HttpResponsePartParser)(rawResponse: String): AnyRef = {
    val data = ByteString(rawResponse.stripMargin.replace(EOL, "\n").replace("\n", "\r\n"))
    parser.parse(data) match {
      case Result.Ok(HttpResponse(s, e, h, p), rd, close) ⇒ (s, e.asString, h, p, rd.utf8String, close)
      case Result.Ok(ChunkedResponseStart(HttpResponse(s, EmptyEntity, h, p)), rd, close) ⇒ (s, h, p, rd.utf8String, close)
      case Result.Ok(MessageChunk(body, ext), rd, close) ⇒ (new String(body), ext, rd.utf8String, close)
      case Result.Ok(ChunkedMessageEnd(ext, trailer), rd, close) ⇒ (ext, trailer, rd.utf8String, close)
      case Result.ParsingError(BadRequest, info) ⇒ info.formatPretty
      case x ⇒ x
    }
  }
}
