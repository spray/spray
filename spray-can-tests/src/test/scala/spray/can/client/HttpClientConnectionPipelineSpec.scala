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

package spray.can.client

import javax.net.ssl.SSLEngine
import java.net.InetSocketAddress
import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.event.LoggingAdapter
import akka.io.Tcp
import akka.actor.ActorContext
import akka.util.ByteString
import akka.testkit.TestProbe
import spray.testkit.RawSpecs2PipelineStageTest
import spray.io.{ Pipeline, SslTlsContext }
import spray.can.Http
import spray.can.TestSupport._
import spray.http._

class HttpClientConnectionPipelineSpec extends Specification with RawSpecs2PipelineStageTest with NoTimeConversions {
  type Context = SslTlsContext

  val stage = HttpClientConnection.pipelineStage(ClientConnectionSettings(system))

  "The HttpClient pipeline" should {

    "send out a simple (unacked) HttpRequest to the server" in new Fixture(stage) {
      connectionActor ! Http.MessageCommand(HttpRequest())
      commands.expectMsgPF() {
        case Tcp.Write(StringBytes(data), _: Tcp.NoAck) ⇒ data
      } === emptyRawRequest()
    }

    "send out a simple (acked) HttpRequest to the server" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest().withAck('Ack)))
      commands.expectMsgPF() {
        case Tcp.Write(StringBytes(data), Pipeline.AckEvent('Ack)) ⇒ data
      } === emptyRawRequest()
      connectionActor ! Pipeline.AckEvent('Ack)
      commands.expectMsg(Pipeline.Tell(probe.ref, 'Ack, connectionActor))
    }

    "dispatch a simple incoming HttpResponse back to the sender" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      connectionActor ! Tcp.Received(ByteString(rawResponse))
      commands.expectMsg(Pipeline.Tell(probe.ref, response, connectionActor))
    }

    "dispatch a keep-alive HttpResponse back to the sender" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      connectionActor ! Tcp.Received(ByteString(rawResponse("123")))
      commands.expectMsg(Pipeline.Tell(probe.ref, response("123"), connectionActor))
      commands.expectNoMsg(100.millis)
    }

    "dispatch a 'Connection: close' HttpResponse back to the sender and close the connection" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      connectionActor ! Tcp.Received(ByteString(rawResponse("123", "Connection: close")))
      commands.expectMsg(Pipeline.Tell(probe.ref, response("123", HttpHeaders.Connection("close")), connectionActor))
      commands.expectMsg(Tcp.Close)
    }

    "be able to deal with PeerClosed events after response completion" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      connectionActor ! Tcp.Received(ByteString(rawResponse("123")))
      connectionActor ! Tcp.PeerClosed
      commands.expectMsg(Pipeline.Tell(probe.ref, response("123"), connectionActor))
      commands.expectNoMsg(100.millis)
    }

    "dispatch an aggregated chunked response back to the sender" in new Fixture(stage) {
      val (probe, probeRef) = probeAndRef()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      Seq(chunkedResponseStart, messageChunk, messageChunk, chunkedMessageEnd) foreach { msg ⇒
        connectionActor ! Tcp.Received(ByteString(msg))
      }
      commands.expectMsgPF() {
        case Pipeline.Tell(`probeRef`, response: HttpResponse, `connectionActor`) ⇒ response.entity
      } === HttpEntity("body123body123")
    }

    "properly complete a 3 requests pipelined dialog" in new Fixture(stage) {
      val (probe1, probeRef1) = probeAndRef()
      val (probe2, probeRef2) = probeAndRef()
      probe1.send(connectionActor, Http.MessageCommand(HttpRequest(entity = "Request 1")))
      probe2.send(connectionActor, Http.MessageCommand(HttpRequest(entity = "Request 2")))
      probe1.send(connectionActor, Http.MessageCommand(HttpRequest(entity = "Request 3")))
      commands.expectMsgPF() { case Tcp.Write(StringBytes(data), _: Tcp.NoAck) ⇒ data } === rawRequest("Request 1")
      commands.expectMsgPF() { case Tcp.Write(StringBytes(data), _: Tcp.NoAck) ⇒ data } === rawRequest("Request 2")
      commands.expectMsgPF() { case Tcp.Write(StringBytes(data), _: Tcp.NoAck) ⇒ data } === rawRequest("Request 3")

      connectionActor ! Tcp.Received(ByteString(rawResponse("Response 1")))
      connectionActor ! Tcp.Received(ByteString(rawResponse("Response 2")))
      connectionActor ! Tcp.Received(ByteString(rawResponse("Response 3")))
      commands.expectMsg(Pipeline.Tell(`probeRef1`, response("Response 1"), connectionActor))
      commands.expectMsg(Pipeline.Tell(`probeRef2`, response("Response 2"), connectionActor))
      commands.expectMsg(Pipeline.Tell(`probeRef1`, response("Response 3"), connectionActor))
    }

    "properly handle responses to HEAD requests" in new Fixture(stage) {
      val (probe, probeRef) = probeAndRef()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest(method = HttpMethods.HEAD)))
      commands.expectMsgPF() {
        case Tcp.Write(StringBytes(data), _: Tcp.NoAck) ⇒ data
      } === emptyRawRequest(method = "HEAD")

      connectionActor ! Tcp.Received(ByteString(prep {
        """|HTTP/1.1 200 OK
           |Server: spray/1.0
           |Date: Thu, 25 Aug 2011 09:10:29 GMT
           |Content-Length: 8
           |Content-Type: text/plain
           |
           |"""
      }))
      commands.expectMsg(Pipeline.Tell(`probeRef`, response("12345678").withEntity(""), connectionActor))
    }

    "properly parse and dispatch 'to-close' responses" in new Fixture(stage) {
      val (probe, probeRef) = probeAndRef()
      probe.send(connectionActor, Http.MessageCommand(HttpRequest()))
      commands.expectMsgType[Tcp.Write]

      connectionActor ! Tcp.Received(ByteString(prep {
        """|HTTP/1.1 200 OK
           |Server: spray/1.0
           |Date: Thu, 25 Aug 2011 09:10:29 GMT
           |Connection: close
           |
           |Yeah"""
      }))
      commands.expectNoMsg(100.millis)

      connectionActor ! Tcp.PeerClosed
      commands.expectMsgPF() {
        case Pipeline.Tell(`probeRef`, HttpResponse(StatusCodes.OK, entity, _, _), `connectionActor`) ⇒ entity
      } === HttpEntity(ContentTypes.`application/octet-stream`, "Yeah")
    }

    "dispatch Closed events to the Close commander" in new Fixture(stage) {
      val probe = TestProbe()
      probe.send(connectionActor, Http.Close)
      commands expectMsg Tcp.Close
      connectionActor ! Tcp.Closed
      commands.expectMsg(Pipeline.Tell(probe.ref, Http.Closed, connectionActor))
    }

    "dispatch SendFailed messages to the sender of the request if the request could not be written" in new Fixture(stage) {
      val probe = TestProbe()
      val request = HttpRequest(entity = "abc")
      probe.send(connectionActor, Http.MessageCommand(request))
      val write = commands.expectMsgType[Tcp.Write]
      connectionActor ! Tcp.CommandFailed(write)
      commands.expectMsg(Pipeline.Tell(probe.ref, Http.SendFailed(request), connectionActor))
    }
  }

  override lazy val config: Config = ConfigFactory.parseString("""
    spray.can.client {
      user-agent-header = spray/1.0
      idle-timeout = 50 ms
      reaping-cycle = infinite  # don't enable the TickGenerator
    }""")

  override def createPipelineContext(_actorContext: ActorContext, _remoteAddress: InetSocketAddress,
                                     _localAddress: InetSocketAddress, _log: LoggingAdapter) =
    new SslTlsContext {
      def actorContext: ActorContext = _actorContext
      def remoteAddress: InetSocketAddress = _remoteAddress
      def localAddress: InetSocketAddress = _localAddress
      def log: LoggingAdapter = _log
      def sslEngine: Option[SSLEngine] = None
    }
}
