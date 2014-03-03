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

package spray.can.server

import java.net.Socket
import java.io.{ InputStreamReader, BufferedReader, OutputStreamWriter, BufferedWriter }
import com.typesafe.config.{ ConfigFactory, Config }
import scala.annotation.tailrec
import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.{ Terminated, ActorRef, ActorSystem }
import akka.io.IO
import akka.testkit.TestProbe
import spray.can.Http
import spray.util._
import spray.util.Utils.temporaryServerHostnameAndPort
import spray.httpx.RequestBuilding._
import spray.http._
import HttpProtocols._
import spray.can.Http.RegisterChunkHandler
import spray.can.client.ClientConnectionSettings
import spray.io.CommandWrapper

class SprayCanServerSpec extends Specification with NoTimeConversions {
  val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = ERROR
      io.tcp.trace-logging = off
    }
    spray.can.server.request-chunk-aggregation-limit = 0
    spray.can.server.pipelining-limit = 4
    spray.can.server.verbose-error-messages = on
    spray.can.client.response-chunk-aggregation-limit = 0""")
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)

  "The server-side spray-can HTTP infrastructure" should {

    "properly bind and unbind an HttpListener" in new TestSetup {
      val commander = TestProbe()
      commander.send(listener, Http.Unbind)
      commander expectMsg Http.Unbound
    }
    "properly bind and unbind an HttpListener with graceperiod" in new TestSetup {
      val commander = TestProbe()
      val clientTerminationWatcher = TestProbe()
      val serverTerminationWatcher = TestProbe()

      val clientSettings = ClientConnectionSettings {
        """spray.can.client.idleTimeout = infinite
          |spray.can.client.requestTimeout = infinite
        """.stripMargin
      }

      val connection = openNewClientConnection(Some(clientSettings))
      val serverSide = acceptConnection()
      clientTerminationWatcher.watch(connection)
      serverTerminationWatcher.watch(listener)
      commander.send(listener, Http.Unbind(10.minutes))
      commander expectMsg Http.Unbound
      commander.expectNoMsg()
      clientTerminationWatcher.expectNoMsg()
      serverTerminationWatcher.expectNoMsg()

      connection ! Http.Close
      clientTerminationWatcher.expectMsgType[Terminated].actor === connection
      serverTerminationWatcher.expectMsgType[Terminated].actor === listener
    }

    "properly complete a simple request/response cycle" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probe = sendRequest(connection, Get("/abc"))
      serverHandler.expectMsgType[HttpRequest].uri === Uri(s"http://$hostname:$port/abc")

      serverHandler.reply(HttpResponse(entity = "yeah"))
      probe.expectMsgType[HttpResponse].entity === HttpEntity("yeah")
    }
    "allow handling a request without a sender being set" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probe = sendRequest(connection, Get("/abc"))
      serverHandler.expectMsgType[HttpRequest].uri === Uri(s"http://$hostname:$port/abc")

      serverHandler.sender ! HttpResponse(entity = "yeah") // sender is null at receive
      probe.expectMsgType[HttpResponse].entity === HttpEntity("yeah")
    }

    "properly complete a chunked request/response cycle" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probe = sendRequest(connection, ChunkedRequestStart(Get("/abc")))
      serverHandler.expectMsgType[ChunkedRequestStart].request.uri === Uri(s"http://$hostname:$port/abc")
      probe.send(connection, MessageChunk("123"))

      val chunkHandler = TestProbe()
      serverHandler.reply(RegisterChunkHandler(chunkHandler.ref))

      probe.send(connection, MessageChunk("456"))

      chunkHandler.expectMsg(MessageChunk("123"))
      chunkHandler.expectMsg(MessageChunk("456"))
      probe.send(connection, ChunkedMessageEnd)
      chunkHandler.expectMsg(ChunkedMessageEnd)

      chunkHandler.reply(ChunkedResponseStart(HttpResponse(entity = "yeah")))
      chunkHandler.reply(MessageChunk("234"))
      chunkHandler.reply(MessageChunk("345"))
      chunkHandler.reply(ChunkedMessageEnd)
      probe.expectMsgType[ChunkedResponseStart].response.entity === HttpEntity.Empty
      probe.expectMsg(MessageChunk("yeah"))
      probe.expectMsg(MessageChunk("234"))
      probe.expectMsg(MessageChunk("345"))
      probe.expectMsg(ChunkedMessageEnd)
      serverHandler.expectNoMsg()
    }

    "maintain response order for pipelined requests" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probeA = sendRequest(connection, Get("/a"))
      val probeB = sendRequest(connection, Get("/b"))
      serverHandler.expectMsgType[HttpRequest].uri.path.toString === "/a"
      val responderA = serverHandler.sender
      serverHandler.expectMsgType[HttpRequest].uri.path.toString === "/b"
      serverHandler.reply(HttpResponse(entity = "B"))
      serverHandler.send(responderA, HttpResponse(entity = "A"))

      probeA.expectMsgType[HttpResponse].entity === HttpEntity("A")
      probeB.expectMsgType[HttpResponse].entity === HttpEntity("B")
    }

    "automatically produce an error response" in {
      def errorTest(request: String, errorMsg: String) =
        new TestSetup {
          val socket = openClientSocket()
          val serverHandler = acceptConnection()
          write(socket, request + "\r\n\r\n")
          val (text, _) = readAll(socket)()
          socket.close()
          serverHandler.expectMsg(Http.ConfirmedClosed)
          text must startWith("HTTP/1.1 400 Bad Request")
          text must endWith(errorMsg)
        }
      "when an HTTP/1.1 request has no Host header" in errorTest(
        request = "GET / HTTP/1.1",
        errorMsg = "Request is missing required `Host` header")
      "when an HTTP/1.0 request has no Host header and no default-host-header is configured" in errorTest(
        request = "GET / HTTP/1.0",
        errorMsg = "request has a relative URI and is missing a `Host` header")
      "when an HTTP/1.1 request has an empty Host header and no default-host-header is configured" in errorTest(
        request = "GET / HTTP/1.1\r\nHost:",
        errorMsg = "request has a relative URI and an empty `Host` header")
      "when an HTTP/1.0 request has an empty Host header and no default-host-header is configured" in errorTest(
        request = "GET / HTTP/1.0\r\nHost:",
        errorMsg = "request has a relative URI and an empty `Host` header")
      "when the request has an ill-formed URI" in errorTest(
        request = "GET http://host:naaa HTTP/1.1",
        errorMsg =
          """Illegal request-target, unexpected end-of-input at position 16:\u0020
            |http://host:naaa
            |                ^
            |""".stripMarginWithNewline("\n")) // UriParser Exceptions are rendered with \n
      "when the request has an URI with a fragment" in errorTest(
        request = "GET /path?query#fragment HTTP/1.1",
        errorMsg =
          """Illegal request-target, unexpected character '#' at position 11:\u0020
            |/path?query#fragment
            |           ^
            |""".stripMarginWithNewline("\n")) // UriParser Exceptions are rendered with \n
      "when the request has an absolute URI without authority part and a non-empty host header" in errorTest(
        request = "GET http:/foo HTTP/1.1\r\nHost: spray.io",
        errorMsg = "'Host' header value doesn't match request target authority")
      "when the request has an absolute URI and the authority doesn't match the host header" in errorTest(
        request = "GET http://foo/bar HTTP/1.1\r\nHost: spray.io",
        errorMsg = "'Host' header value doesn't match request target authority")
    }

    "accept requests if a non-empty default-host-header is configured" in {
      def test(request: String, dispatched: HttpRequest) =
        new TestSetup {
          override def configOverrides = """spray.can.server.default-host-header="spray.io:8765""""
          val socket = openClientSocket()
          val serverHandler = acceptConnection()
          val writer = write(socket, request + "\r\n\r\n")
          serverHandler.expectMsgType[HttpRequest] === dispatched
          socket.close()
        }
      "an HTTP/1.0 request without `Host` header and an absolute URI" in test(
        request = "GET http://foo/bar HTTP/1.0",
        dispatched = HttpRequest(uri = Uri("http://foo/bar"), protocol = `HTTP/1.0`))
      "an HTTP/1.0 request without `Host` header and a relative URI" in test(
        request = "GET /foo HTTP/1.0",
        dispatched = HttpRequest(uri = Uri("http://spray.io:8765/foo"), protocol = `HTTP/1.0`))
      "an HTTP/1.1 request with empty `Host` header and a relative URI" in test(
        request = "GET /foo HTTP/1.1\r\nHost:",
        dispatched = HttpRequest(uri = Uri("http://spray.io:8765/foo"), headers = List(HttpHeaders.Host.empty)))
    }

    "properly support fastPath responses" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection {
        case HttpRequest(_, Uri.Path("/abc"), _, _, _) ⇒ HttpResponse(entity = "fast")
      }
      val probe = sendRequest(connection, Get("/abc"))
      serverHandler.expectNoMsg(100.millis)
      probe.expectMsgType[HttpResponse].entity === HttpEntity("fast")
    }

    "provide access to the undecoded URI via a header if spray.can.raw-request-uri-header is enabled" in {
      class RawRequestTestSetup(target: String, protocol: HttpProtocol) extends TestSetup {
        def request: List[String] = s"GET $target $protocol" :: Nil
        def headers: List[HttpHeader] = HttpHeaders.`Raw-Request-URI`(target) :: Nil
        override def configOverrides = """spray.can.server.raw-request-uri-header=true"""
        val socket = openClientSocket()
        val serverHandler = acceptConnection()
        val writer = write(socket, request.mkString("\r\n") + "\r\n\r\n")
        val dispatched = HttpRequest(uri = "http://example.com/foobar?q=baz", protocol = protocol, headers = headers)
        serverHandler.expectMsgType[HttpRequest] === dispatched
        socket.close()
      }
      class RawRequestHostTestSetup(target: String, protocol: HttpProtocol) extends RawRequestTestSetup(target, protocol) {
        override def request = super.request :+ "Host: example.com"
        override def headers = super.headers :+ HttpHeaders.Host("example.com")
      }

      "when a HTTP/1.1 request includes a relative URI" in new RawRequestHostTestSetup(
        target = "/f%6f%6fbar?q=b%61z",
        protocol = `HTTP/1.1`)
      "when a HTTP/1.1 request includes an absolute URI" in new RawRequestHostTestSetup(
        target = "http://ex%61mple.com/f%6f%6fbar?q=b%61z",
        protocol = `HTTP/1.1`)
      "when a HTTP/1.0 request includes a relative URI" in new RawRequestHostTestSetup(
        target = "/f%6f%6fbar?q=b%61z",
        protocol = `HTTP/1.0`)
      "when a HTTP/1.0 request includes an absolute URI" in new RawRequestTestSetup(
        target = "http://ex%61mple.com/f%6f%6fbar?q=b%61z",
        protocol = `HTTP/1.0`)
    }
    "allow changing timeouts with SetRequestTimeout" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probe = sendRequest(connection, Get("/abc"))
      serverHandler.expectMsgType[HttpRequest].uri === Uri(s"http://$hostname:$port/abc")
      val sender = serverHandler.sender

      serverHandler.send(sender, SetRequestTimeout(100.millis))
      serverHandler.send(sender, SetTimeoutTimeout(100.millis))
      serverHandler.expectMsgType[Timedout](1.second)
      probe.expectMsgType[HttpResponse](1.second).entity ===
        HttpEntity("Ooops! The server was not able to produce a timely response to your request.\n" +
          "Please try again in a short while!")
    }
  }

  step {
    val probe = TestProbe()
    probe.send(IO(Http), Http.CloseAll)
    probe.expectMsg(Http.ClosedAll)
    system.shutdown()
  }

  class TestSetup extends org.specs2.specification.Scope {
    val (hostname, port) = temporaryServerHostnameAndPort()
    val bindHandler = TestProbe()
    def configOverrides = ""

    // automatically bind a server
    val listener = {
      val commander = TestProbe()
      val settings = spray.util.pimpString_(configOverrides).toOption.map(ServerSettings.apply)
      commander.send(IO(Http), Http.Bind(bindHandler.ref, hostname, port, settings = settings))
      commander.expectMsgType[Http.Bound]
      commander.sender
    }

    def openNewClientConnection(settings: Option[ClientConnectionSettings] = None): ActorRef = {
      val probe = TestProbe()
      probe.send(IO(Http), Http.Connect(hostname, port, settings = settings))
      probe.expectMsgType[Http.Connected]
      probe.sender
    }

    def acceptConnection(fastPath: Http.FastPath = Http.EmptyFastPath): TestProbe = {
      bindHandler.expectMsgType[Http.Connected]
      val probe = TestProbe()
      bindHandler.reply(Http.Register(probe.ref, fastPath = fastPath))
      probe
    }

    def sendRequest(connection: ActorRef, part: HttpRequestPart): TestProbe = {
      val probe = TestProbe()
      probe.send(connection, part)
      probe
    }

    def openClientSocket() = new Socket(hostname, port)

    def write(socket: Socket, data: String) = {
      val writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
      writer.write(data)
      writer.flush()
      writer
    }

    def readAll(socket: Socket)(reader: BufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream))): (String, BufferedReader) = {
      val sb = new java.lang.StringBuilder
      val cbuf = new Array[Char](256)
      @tailrec def drain(): (String, BufferedReader) = reader.read(cbuf) match {
        case -1 ⇒ sb.toString -> reader
        case n  ⇒ sb.append(cbuf, 0, n); drain()
      }
      drain()
    }
  }
}
