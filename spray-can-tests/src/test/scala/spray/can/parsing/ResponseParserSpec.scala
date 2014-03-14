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

import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import scala.annotation.tailrec
import akka.actor.ActorSystem
import akka.util.ByteString
import spray.util._
import spray.http._
import HttpHeaders._
import HttpMethods._
import StatusCodes._
import HttpProtocols._
import spray.can.TestSupport

class ResponseParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    spray.can.parsing.max-response-reason-length = 21
    spray.can.parsing.incoming-auto-chunking-threshold-size = 20""")
  val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)

  "The response parsing logic" should {
    "properly parse" in {

      // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3.3 (1)
      "a 200 response to a HEAD request" in {
        parse(HEAD) {
          """HTTP/1.1 200 OK
            |
            |HTT"""
        } === Seq(OK, "", Nil, `HTTP/1.1`, 'dontClose)
      }

      // http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-22#section-3.3.3 (1)
      "a 204 response" in {
        parse {
          """HTTP/1.1 204 OK
            |
            |"""
        } === Seq(NoContent, "", Nil, `HTTP/1.1`, 'dontClose)
      }

      "a response with a custom status code" in {
        TestSupport.ServerOnTheMove // make sure status code is registered
        parse {
          """HTTP/1.1 330 Server on the move
            |Content-Length: 0
            |
            |"""
        } === Seq(TestSupport.ServerOnTheMove, "", List(`Content-Length`(0)), `HTTP/1.1`, 'dontClose)
      }

      "a response with one header, a body, but no Content-Length header" in {
        parse("""HTTP/1.0 404 Not Found
          |Host: api.example.com
          |
          |Foobs""", "") === Seq(NotFound, "Foobs", List(Host("api.example.com")), `HTTP/1.0`, 'close, Result.IgnoreAllFurtherInput)
      }

      "a response with one header, no body, and no Content-Length header" in {
        parse("""HTTP/1.0 404 Not Found
          |Host: api.example.com
          |
          |""", "") === Seq(NotFound, "", List(Host("api.example.com")), `HTTP/1.0`, 'close, Result.IgnoreAllFurtherInput)
      }

      "a response with 3 headers, a body and remaining content" in {
        parse {
          """HTTP/1.1 500 Internal Server Error
            |User-Agent: curl/7.19.7 xyz
            |Connection:close
            |Content-Length: 17
            |
            |Shake your BOODY!HTTP/1."""
        } === Seq(InternalServerError, "Shake your BOODY!", List(`Content-Length`(17), Connection("close"),
          `User-Agent`("curl/7.19.7 xyz")), `HTTP/1.1`, 'close)
      }

      "a split response (parsed byte-by-byte)" in {
        val response = prep {
          """HTTP/1.1 200 Ok
            |Content-Length: 4
            |
            |ABCD"""
        }
        rawParse(newParser())(response.toCharArray.map(_.toString)(collection.breakOut): _*) ===
          Seq(OK, "ABCD", List(`Content-Length`(4)), `HTTP/1.1`, 'dontClose)
      }
    }

    "properly parse a chunked" in {
      val start =
        """HTTP/1.1 200 OK
          |Transfer-Encoding: chunked
          |Server: spray-can
          |
          |"""
      val startMatch = Seq(OK, List(Server("spray-can"), `Transfer-Encoding`("chunked")), `HTTP/1.1`, 'dontClose)

      "response start" in {
        parse(start + "rest") === startMatch :+ "Illegal character 'r' in chunk start"
      }

      "message chunk with and without extension" in {
        parse(start,
          """3
            |abc
            |""",
          """10;some=stuff;bla
            |0123456789ABCDEF
            |""") === startMatch ++ Seq(
            "abc", "", 'dontClose,
            "0123456789ABCDEF", "some=stuff;bla", 'dontClose)
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
            |HTTP/""") === startMatch ++ Seq("nice=true", List(RawHeader("Bar", "xyz"), RawHeader("Foo", "pip apo")), 'dontClose)
      }
    }

    "properly auto-chunk with content-length given" in {
      def start(contentSize: Int) =
        f"""HTTP/1.1 200 OK
           |Content-Length: $contentSize%d
           |Server: spray-can
           |
           |"""
      def startMatch(content: String = null)(len: Int = content.length) =
        (if (content == null) List(OK) else List(OK, content)) :::
          List(List(Server("spray-can"), `Content-Length`(len)), `HTTP/1.1`, 'dontClose)

      "full response if size < incoming-auto-chunking-threshold-size" in {
        parse(start(1) + "rH") === startMatch("r")()
      }

      "response start" in {
        parse(start(25) + "yeah") === startMatch()(25) ++ Seq("yeah", "", 'dontClose)
      }

      "response chunk" in {
        parse(start(25), "yeah1", "yeah2") === startMatch()(25) ++ Seq(
          "yeah1", "", 'dontClose,
          "yeah2", "", 'dontClose)
      }
      "response end" in {
        parse(start(25), "yeah1", "yeah2", "yeah3", "yeah4", "yeah5HTTP") === startMatch()(25) ++ Seq(
          "yeah1", "", 'dontClose,
          "yeah2", "", 'dontClose,
          "yeah3", "", 'dontClose,
          "yeah4", "", 'dontClose,
          "yeah5", "", 'dontClose,
          "", Nil, 'dontClose)
      }
    }

    "properly auto-chunk without content-length" in {
      val start =
        """HTTP/1.1 200 OK
           |Server: spray-can
           |
           |"""

      "full response if size < incoming-auto-chunking-threshold-size" in {
        parse(start + "yeah", "") === Seq(OK, "yeah", List(Server("spray-can")), `HTTP/1.1`, 'close, Result.IgnoreAllFurtherInput)
      }

      "response start" in {
        // 21 bytes means should now be in chunking mode
        parse(start + "rest1rest2rest3rest41", "more") === Seq(
          OK, List(Server("spray-can")), `HTTP/1.1`, 'close,
          "rest1rest2rest3rest41", "", 'close,
          "more", "", 'close)
      }

      "response end" in {
        parse(start + "rest1rest2rest3rest41", "more1", "more2", "") === Seq(
          OK, List(Server("spray-can")), `HTTP/1.1`, 'close,
          "rest1rest2rest3rest41", "", 'close,
          "more1", "", 'close,
          "more2", "", 'close,
          "", Nil, 'close, Result.IgnoreAllFurtherInput)
      }
    }

    "reject a response with" in {
      "HTTP version 1.2" in {
        parse("HTTP/1.2 200 OK\r\n") === Seq("The server-side HTTP version is not supported")
      }

      "an illegal status code" in {
        parse("HTTP/1.1 2000 Something") === Seq("Illegal response status code")
      }

      "a too-long response status reason" in {
        parse("HTTP/1.1 204 1234567890123456789012\r\n") ===
          Seq("Response reason phrase exceeds the configured limit of 21 characters")
      }
    }
  }

  step(system.shutdown())

  def newParser(requestMethod: HttpMethod = GET) = {
    val parser = new HttpResponsePartParser(ParserSettings(system))()
    parser.setRequestMethodForNextResponse(requestMethod)
    parser
  }

  def parse(rawResponse: String*): AnyRef = parse(GET)(rawResponse: _*)
  def parse(requestMethod: HttpMethod)(rawResponse: String*): AnyRef = parse(newParser(requestMethod))(rawResponse: _*)
  def parse(parser: Parser)(rawResponse: String*): Seq[Any] = rawParse(parser)(rawResponse map prep: _*)

  def rawParse(parser: Parser)(rawResponse: String*): Seq[Any] = {
    def closeSymbol(close: Boolean) = if (close) 'close else 'dontClose
    @tailrec def rec(current: Result, remainingData: List[ByteString], result: Seq[Any] = Seq.empty): Seq[Any] =
      current match {
        case Result.Emit(HttpResponse(s, e, h, p), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(s, e.asString, h, p, closeSymbol(c)))
        case Result.Emit(ChunkedResponseStart(HttpResponse(s, HttpEntity.Empty, h, p)), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(s, h, p, closeSymbol(c)))
        case Result.Emit(MessageChunk(d, ext), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(d.asString, ext, closeSymbol(c)))
        case Result.Emit(ChunkedMessageEnd(ext, trailer), c, continue) ⇒
          rec(continue(), remainingData, result ++ Seq(ext, trailer, closeSymbol(c)))
        case Result.NeedMoreData(p) ⇒
          if (remainingData.nonEmpty) rec(p(remainingData.head), remainingData.tail, result) else result
        case Result.ParsingError(BadRequest, info) ⇒ result :+ info.formatPretty
        case x                                     ⇒ result :+ x
      }

    val data: List[ByteString] = rawResponse.map(ByteString.apply)(collection.breakOut)
    rec(parser(data.head), data.tail)
  }

  def prep(response: String) = response.stripMarginWithNewline("\r\n")
}
