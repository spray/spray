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

package spray.can.server

import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import akka.io.Tcp
import akka.util.ByteString
import akka.actor.{ ActorRef, ActorContext }
import akka.event.LoggingAdapter
import akka.testkit.TestProbe
import spray.testkit.RawSpecs2PipelineStageTest
import spray.io.{ TickGenerator, Pipeline, SslTlsContext }
import spray.io.Pipeline.Tell
import spray.can.Http
import spray.http._
import spray.can.TestSupport._
import HttpHeaders._
import MediaTypes._

class HttpServerConnectionPipelineSpec extends Specification with RawSpecs2PipelineStageTest {
  type Context = ServerFrontend.Context with SslTlsContext

  "The HttpServer pipeline" should {

    "dispatch a simple HttpRequest to a singleton service actor" in new MyFixture() {
      connectionActor ! Tcp.Received(ByteString(simpleRequest))
      commands.expectMsgPF() {
        case Pipeline.Tell(`handlerRef`, msg, _) ⇒ msg
      } === HttpRequest(uri = "http://test.com/", headers = List(`Host`("test.com")))
    }

    "dispatch an aggregated chunked requests" in new MyFixture(requestChunkAggregation = true) {
      Seq(chunkedRequestStart, messageChunk, messageChunk, chunkedMessageEnd) foreach { msg ⇒
        connectionActor ! Tcp.Received(ByteString(msg))
      }
      commands.expectMsgPF() {
        case Pipeline.Tell(`handlerRef`, msg, _) ⇒ msg
      } === HttpRequest(
        uri = "http://test.com/",
        headers = List(
          `Transfer-Encoding`("chunked"),
          `Content-Type`(`text/plain`),
          `Host`("test.com")),
        entity = HttpEntity("body123body123"))
    }

    "dispatch Ack messages" in {
      "to the sender of an HttpResponse" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        requestSender.tell(Http.MessageCommand(HttpResponse().withAck(1)), probe.ref)
        val Tcp.Write(StringBytes(data), ack) = commands.expectMsgType[Tcp.Write]
        wipeDate(data) === prep {
          """HTTP/1.1 200 OK
            |Server: spray/test
            |Date: XXXX
            |Content-Length: 0
            |
            |"""
        }

        connectionActor ! ack
        commands.expectMsg(Pipeline.Tell(probe.ref, 1, requestSender))
      }

      "to the senders of a ChunkedResponseStart, MessageChunk and ChunkedMessageEnd" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val (probe1, probe2, probe3, probe4) = (TestProbe(), TestProbe(), TestProbe(), TestProbe())
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse()).withAck(1)), probe1.ref)
        val Tcp.Write(StringBytes(data1), ack1) = commands.expectMsgType[Tcp.Write]
        wipeDate(data1) === prep {
          """HTTP/1.1 200 OK
            |Server: spray/test
            |Date: XXXX
            |Transfer-Encoding: chunked
            |
            |"""
        }
        connectionActor ! ack1
        commands.expectMsg(Pipeline.Tell(probe1.ref, 1, requestSender))

        requestSender.tell(Http.MessageCommand(MessageChunk("part 1").withAck(2)), probe2.ref)
        val Tcp.Write(StringBytes(data2), ack2) = commands.expectMsgType[Tcp.Write]
        data2 === prep("6\npart 1\n")
        connectionActor ! ack2
        commands.expectMsg(Pipeline.Tell(probe2.ref, 2, requestSender))

        requestSender.tell(Http.MessageCommand(MessageChunk("part 2")), probe2.ref)
        val Tcp.Write(StringBytes(data), _: Tcp.NoAck) = commands.expectMsgType[Tcp.Write]
        data === prep("6\npart 2\n")

        requestSender.tell(Http.MessageCommand(MessageChunk("part 3").withAck(3)), probe3.ref)
        val Tcp.Write(StringBytes(data3), ack3) = commands.expectMsgType[Tcp.Write]
        data3 === prep("6\npart 3\n")
        connectionActor ! ack3
        commands.expectMsg(Pipeline.Tell(probe3.ref, 3, requestSender))

        requestSender.tell(Http.MessageCommand(ChunkedMessageEnd.withAck(4)), probe4.ref)
        val Tcp.Write(StringBytes(data4), ack4) = commands.expectMsgType[Tcp.Write]
        data4 === prep("0\n\n")
        connectionActor ! ack4
        commands.expectMsg(Pipeline.Tell(probe4.ref, 4, requestSender))
      }
    }

    "if the response could not be written dispatch SendFailed messages" in {
      "to the sender of an HttpResponse" in new MyFixture(handleBackpressure = false) {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        val response = HttpResponse(entity = "abc")
        requestSender.tell(Http.MessageCommand(response), probe.ref)
        val write = commands.expectMsgType[Tcp.Write]
        connectionActor ! Tcp.CommandFailed(write)
        commands.expectMsg(Pipeline.Tell(probe.ref, Http.SendFailed(response), connectionActor))
      }
    }

    "dispatch Closed messages" in {
      "to the handler if no request is open" in new MyFixture() {
        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, connectionActor))
      }
      "to the handler if a request is open" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender
        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, requestSender))
      }
      "to the response sender if a response has been sent but not yet confirmed" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        requestSender.tell(Http.MessageCommand(HttpResponse().withAck(1)), probe.ref)
        commands.expectMsgType[Tcp.Write]

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(probe.ref, Http.Closed, requestSender))
      }
      "to the handler if a response has been sent and confirmed" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        requestSender.tell(Http.MessageCommand(HttpResponse().withAck(1)), probe.ref)
        connectionActor ! commands.expectMsgType[Tcp.Write].ack
        commands.expectMsg(Pipeline.Tell(probe.ref, 1, requestSender))

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, connectionActor))
      }
      "to the handler if a response has been sent without ack" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        requestSender.tell(Http.MessageCommand(HttpResponse()), probe.ref)
        commands.expectMsgType[Tcp.Write]

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, connectionActor))
      }
      "to the response sender of a chunk stream if a chunk has been sent but not yet confirmed" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val probe = TestProbe()
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse()).withAck(1)), probe.ref)
        commands.expectMsgType[Tcp.Write]

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(probe.ref, Http.Closed, requestSender))
      }
      "to the last response sender of a chunk stream if a chunk has been sent and confirmed" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val (probe1, probe2) = (TestProbe(), TestProbe())
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse())), probe1.ref)
        requestSender.tell(Http.MessageCommand(MessageChunk("bla").withAck(12)), probe2.ref)
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("Transfer-Encoding: chunked")
        connectionActor ! commands.expectMsgType[Tcp.Write].ack
        commands.expectMsg(Pipeline.Tell(probe2.ref, 12, requestSender))

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(probe2.ref, Http.Closed, requestSender))
      }
      "to the last response sender if a final chunk has been sent but not yet confirmed" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val (probe1, probe2, probe3) = (TestProbe(), TestProbe(), TestProbe())
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse())), probe1.ref)
        requestSender.tell(Http.MessageCommand(MessageChunk("bla")), probe2.ref)
        requestSender.tell(Http.MessageCommand(ChunkedMessageEnd.withAck(16)), probe3.ref)
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("Transfer-Encoding: chunked")
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("bla")
        commands.expectMsgType[Tcp.Write]

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(probe3.ref, Http.Closed, requestSender))
      }
      "to the handler if a final chunk has been sent and no confirmation is open (without ack)" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val (probe1, probe2, probe3) = (TestProbe(), TestProbe(), TestProbe())
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse())), probe1.ref)
        requestSender.tell(Http.MessageCommand(MessageChunk("bla")), probe2.ref)
        requestSender.tell(Http.MessageCommand(ChunkedMessageEnd), probe3.ref)
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("Transfer-Encoding: chunked")
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("bla")
        commands.expectMsgType[Tcp.Write]

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, connectionActor))
      }
      "to the handler if a final chunk has been sent and no confirmation is open (with completed ack)" in new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(simpleRequest))
        val requestSender = commands.expectMsgType[Tell].sender

        val (probe1, probe2, probe3) = (TestProbe(), TestProbe(), TestProbe())
        requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse())), probe1.ref)
        requestSender.tell(Http.MessageCommand(MessageChunk("bla")), probe2.ref)
        requestSender.tell(Http.MessageCommand(ChunkedMessageEnd.withAck(16)), probe3.ref)
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("Transfer-Encoding: chunked")
        commands.expectMsgType[Tcp.Write].data.utf8String must contain("bla")
        connectionActor ! commands.expectMsgType[Tcp.Write].ack
        commands.expectMsg(Pipeline.Tell(probe3.ref, 16, requestSender))

        connectionActor ! Tcp.Closed
        commands.expectMsg(Pipeline.Tell(handler.ref, Http.Closed, connectionActor))
      }
    }

    "handle 'Expected: 100-continue' headers" in {
      def example(expectValue: String) = new MyFixture() {
        connectionActor ! Tcp.Received(ByteString(prep(
          """GET / HTTP/1.1
            |Host: test.com
            |Content-Type: text/plain
            |Content-Length: 12
            |Expect: %s
            |
            |bodybodybody""" format expectValue)))
        commands.expectMsgType[Tcp.Write].data.utf8String === prep("HTTP/1.1 100 Continue\n\n")
        commands.expectMsgType[Pipeline.Tell].message === HttpRequest(
          uri = "http://test.com/",
          headers = List(
            Expect(expectValue),
            `Content-Length`(12),
            `Content-Type`(`text/plain`),
            Host("test.com"))).withEntity("bodybodybody")
      }
      "with a header value fully matching the spec" in example("100-continue")
      "with a header value containing illegal casing" in example("100-Continue")
    }

    "dispatch HEAD requests as GET requests and suppress sending of HttpResponse bodies" in new MyFixture() {
      connectionActor ! Tcp.Received(ByteString(prep(
        """HEAD / HTTP/1.1
          |Host: test.com
          |
          |""")))
      val Pipeline.Tell(`handlerRef`, message, requestSender) = commands.expectMsgType[Pipeline.Tell]
      message === HttpRequest(uri = "http://test.com/", headers = List(`Host`("test.com")))

      requestSender.tell(Http.MessageCommand(HttpResponse(entity = "1234567")), system.deadLetters)
      wipeDate(commands.expectMsgType[Tcp.Write].data.utf8String) === prep {
        """HTTP/1.1 200 OK
          |Server: spray/test
          |Date: XXXX
          |Content-Type: text/plain
          |Content-Length: 7
          |
          |"""
      }
    }

    "dispatch HEAD requests as GET requests and suppress sending of chunked responses" in new MyFixture(handleBackpressure = false) {
      connectionActor ! Tcp.Received(ByteString(prep(
        """HEAD / HTTP/1.1
          |Host: test.com
          |
          |""")))
      val Pipeline.Tell(`handlerRef`, message, requestSender) = commands.expectMsgType[Pipeline.Tell]
      message === HttpRequest(uri = "http://test.com/", headers = List(`Host`("test.com")))

      val probe = TestProbe()
      requestSender.tell(Http.MessageCommand(ChunkedResponseStart(HttpResponse(entity = "1234567"))), probe.ref)
      val Tcp.Write(StringBytes(data), ack) = commands.expectMsgType[Tcp.Write]
      wipeDate(data) === prep {
        """HTTP/1.1 200 OK
          |Server: spray/test
          |Date: XXXX
          |Content-Type: text/plain
          |Content-Length: 7
          |
          |"""
      }
      ack === AckEventWithReceiver(Http.Closed, probe.ref)
    }

    "dispatch Timeout messages in case of a request timeout (and dispatch respective response)" in new MyFixture() {
      connectionActor ! Tcp.Received(ByteString(simpleRequest))
      val requestSender = commands.expectMsgType[Tell].sender
      Thread.sleep(100)
      connectionActor ! TickGenerator.Tick
      commands.expectMsg(Pipeline.Tell(handlerRef,
        Timedout(HttpRequest(uri = "http://test.com/", headers = List(`Host`("test.com")))), `requestSender`))
      requestSender.tell(HttpResponse(), system.deadLetters)
      wipeDate(commands.expectMsgType[Tcp.Write].data.utf8String) === simpleResponse
    }

    "dispatch the default timeout response if the Timeout timed out" in new MyFixture(handleBackpressure = false) {
      connectionActor ! Tcp.Received(ByteString(simpleRequest))
      val requestSender = commands.expectMsgType[Tell].sender
      Thread.sleep(55)
      connectionActor ! TickGenerator.Tick
      commands.expectMsgType[Pipeline.Tell] // Timedout
      Thread.sleep(35)
      connectionActor ! TickGenerator.Tick
      wipeDate(commands.expectMsgType[Tcp.Write].data.utf8String) === prep {
        """HTTP/1.1 500 Internal Server Error
          |Server: spray/test
          |Date: XXXX
          |Content-Type: text/plain
          |Connection: close
          |Content-Length: 111
          |
          |"""
      } + "Ooops! The server was not able to produce a timely response to your request.\n" +
        "Please try again in a short while!"
      commands.expectMsg(Tcp.Close)
    }
  }

  ///////////////////////// SUPPORT ////////////////////////

  val simpleRequest = prep {
    """|GET / HTTP/1.1
     |Host: test.com
     |
     |"""
  }

  val simpleResponse = prep {
    """|HTTP/1.1 200 OK
     |Server: spray/test
     |Date: XXXX
     |Content-Length: 0
     |
     |"""
  }

  def stage(configUpdates: (ServerSettings ⇒ ServerSettings)*) = {
    val updater = configUpdates.reduce(_ andThen _)
    HttpServerConnection.pipelineStage(updater(ServerSettings(system)), None)
  }

  def handleBackpressureSetting(doHandle: Boolean): ServerSettings ⇒ ServerSettings =
    if (doHandle) identity /* default is to handle */ else _.copy(backpressureSettings = None)
  def aggregateRequestChunksSetting(doAggregate: Boolean): ServerSettings ⇒ ServerSettings =
    if (doAggregate) identity else _.copy(requestChunkAggregationLimit = 0)

  override lazy val config: Config = ConfigFactory.parseString("""
    spray.can.server {
      server-header = "spray/test"
      idle-timeout = 250 ms
      request-timeout = 50 ms
      timeout-timeout = 30 ms
      reaping-cycle = infinite  # don't enable the TickGenerator
      pipelining-limit = 10
      stats-support = off
    }""")

  class MyFixture(requestChunkAggregation: Boolean = false, handleBackpressure: Boolean = true)
      extends Fixture(stage(aggregateRequestChunksSetting(requestChunkAggregation), handleBackpressureSetting(handleBackpressure))) { fixture ⇒

    val handler = TestProbe()
    val handlerRef = handler.ref

    override def createPipelineContext(_actorContext: ActorContext, _remoteAddress: InetSocketAddress,
                                       _localAddress: InetSocketAddress, _log: LoggingAdapter) =
      new ServerFrontend.Context with SslTlsContext {
        def handler: ActorRef = fixture.handler.ref
        def fastPath: Http.FastPath = Http.EmptyFastPath
        def actorContext: ActorContext = _actorContext
        def remoteAddress: InetSocketAddress = _remoteAddress
        def localAddress: InetSocketAddress = _localAddress
        def log: LoggingAdapter = _log
        def sslEngine: Option[SSLEngine] = None
      }
  }
}

