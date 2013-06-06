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

import org.specs2.mutable.Specification
import com.typesafe.config.{ ConfigFactory, Config }
import akka.actor.{ ActorRef, Status, ActorSystem }
import akka.io.IO
import akka.testkit.TestProbe
import spray.can.{ HostConnectorInfo, HostConnectorSetup, Http }
import spray.util.Utils._
import spray.httpx.RequestBuilding._
import spray.http._
import HttpHeaders._
import spray.testkit._

class SprayCanClientSpec extends Specification {

  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    akka.io.tcp.trace-logging = off
    spray.can.client.request-timeout = 500ms
    spray.can.host-connector.max-retries = 1
    spray.can.host-connector.client.request-timeout = 500ms
    spray.can.server.request-chunk-aggregation-limit = 0
    spray.can.client.response-chunk-aggregation-limit = 0""")
  implicit val system = ActorSystem(actorSystemNameFrom(getClass), testConf)

  "The connection-level client infrastructure" should {
    "properly complete a simple request/response cycle" in new TestSetup {
      val clientConnection = newClientConnect()
      val client = send(clientConnection, Get("/abc") ~> Host(hostname, port))
      val server = acceptConnection()
      server.expectMsgType[HttpRequest].uri.path.toString === "/abc"
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse].entity === HttpEntity("ok")
      client.send(clientConnection, Http.Close)
      server.expectMsg(Http.PeerClosed)
      client.expectMsg(Http.Closed)
      unbind()
    }

    "properly complete a pipelined request/response cycle with a chunked request" in new TestSetup {
      val clientConnection = newClientConnect()
      val client = send(clientConnection, ChunkedRequestStart(Get("/abc") ~> Host(hostname, port)))
      client.send(clientConnection, MessageChunk("123"))
      client.send(clientConnection, MessageChunk("456"))
      client.send(clientConnection, ChunkedMessageEnd())
      client.send(clientConnection, Get("/def") ~> Host(hostname, port))

      val server = acceptConnection()
      server.expectMsgType[ChunkedRequestStart].request.uri.path.toString === "/abc"
      server.expectMsg(MessageChunk("123"))
      server.expectMsg(MessageChunk("456"))
      server.expectMsg(ChunkedMessageEnd())
      val firstRequestSender = server.sender
      server.expectMsgType[HttpRequest].uri.path.toString === "/def"
      server.reply(HttpResponse(entity = "ok-def")) // reply to the second request first
      server.send(firstRequestSender, HttpResponse(entity = "ok-abc"))

      client.expectMsgType[HttpResponse].entity === HttpEntity("ok-abc")
      client.expectMsgType[HttpResponse].entity === HttpEntity("ok-def")
      client.send(clientConnection, Http.Close)
      server.expectMsg(Http.PeerClosed)
      client.expectMsg(Http.Closed)
      unbind()
    }

    "produce an error if the request times out" in new TestSetup {
      val clientConnection = newClientConnect()
      val request = Get("/def") ~> Host(hostname, port)
      val client = send(clientConnection, request)
      val server = acceptConnection()
      server.expectMsgType[HttpRequest].uri.path.toString === "/def"
      client.expectMsg(Timedout(request))
      client.expectMsg(Http.Closed)
      server.expectMsg(Http.PeerClosed)
      unbind()
    }
  }

  "The host-level client infrastructure" should {
    "return the same HostConnector for identical setup requests" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector1, _) = probe.expectMsgType[HostConnectorInfo]
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector2, _) = probe.expectMsgType[HostConnectorInfo]
      hostConnector1 === hostConnector2
    }

    "properly complete a simple request/response cycle with a Host-header request" in new TestSetup {
      val (probe, hostConnector) = sendViaHostConnector(Get("/hij") ~> Host(hostname, port) ~> Date(DateTime.now))
      verifyServerSideRequestAndReply("http://" + hostname + ":" + port + "/hij", probe)
      closeHostConnector(hostConnector)
    }

    "add a host header to the request if it doesn't contain one" in new TestSetup {
      val (probe, hostConnector) = sendViaHostConnector(Get("/lmn"))
      verifyServerSideRequestAndReply("http://" + hostname + ":" + port + "/lmn", probe)
      closeHostConnector(hostConnector)
    }

    "accept absolute URIs and render them unchanged" in new TestSetup {
      val (probe, hostConnector) = sendViaHostConnector(Get("http://www.example.com/"))
      verifyServerSideRequestAndReply("http://www.example.com/", probe)
      closeHostConnector(hostConnector)
    }

    "support a clean CloseAll shutdown" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector, _) = probe.expectMsgType[HostConnectorInfo]

      // open two connections
      val clientA = send(hostConnector, Get("/a"))
      val clientB = send(hostConnector, Get("/b"))
      val serverA = acceptConnection()
      val serverB = acceptConnection()

      probe.send(hostConnector, Http.CloseAll)
      clientA.expectMsgType[Status.Failure].cause.getMessage == "Connection actively closed"
      clientB.expectMsgType[Status.Failure].cause.getMessage == "Connection actively closed"
      serverA.expectMsgType[HttpRequest]
      serverA.expectMsg(Http.PeerClosed)
      serverB.expectMsgType[HttpRequest]
      serverB.expectMsg(Http.PeerClosed)
      probe.expectMsg(Http.ClosedAll)
    }
  }

  "The request-level client infrastructure" should {
    "properly complete a simple request/response cycle with a request containing a host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc") ~> Host(hostname, port))
      verifyServerSideRequestAndReply("http://" + hostname + ":" + port + "/abc", probe)
    }

    "transform absolute request URIs into relative URIs plus host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("http://" + hostname + ":" + port + "/abc?query#fragment"))
      verifyServerSideRequestAndReply("http://" + hostname + ":" + port + "/abc?query", probe)
    }

    "produce an error if the request does not contain a Host-header or an absolute URI" in {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc"))
      probe.expectMsgType[Status.Failure].cause.getMessage must startWith("Cannot establish effective request URI")
    }

    "produce an error if the request was not completed within the configured timeout" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abcdef-timeout") ~> Host(hostname, port))
      acceptConnection()
      probe.expectMsgType[Status.Failure].cause.getMessage must startWith("Request timeout")
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

    // automatically bind a server
    val listener = {
      val commander = TestProbe()
      commander.send(IO(Http), Http.Bind(bindHandler.ref, hostname, port))
      commander.expectMsgType[Http.Bound]
      commander.sender
    }

    def newClientConnect(): ActorRef = {
      val probe = TestProbe()
      probe.send(IO(Http), Http.Connect(hostname, port))
      probe.expectMsgType[Http.Connected]
      probe.sender
    }

    def acceptConnection(): TestProbe = {
      bindHandler.expectMsgType[Http.Connected]
      val probe = TestProbe()
      bindHandler.reply(Http.Register(probe.ref))
      probe
    }

    def send(transport: ActorRef, part: HttpRequestPart): TestProbe = {
      val probe = TestProbe()
      probe.send(transport, part)
      probe
    }

    def unbind(): Unit = {
      val probe = TestProbe()
      probe.send(listener, Http.Unbind)
      probe.expectMsg(Http.Unbound)
    }

    def sendViaHostConnector(request: HttpRequest): (TestProbe, ActorRef) = {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector, _) = probe.expectMsgType[HostConnectorInfo]
      probe.sender === hostConnector
      probe.reply(request)
      probe -> hostConnector
    }

    def verifyServerSideRequestAndReply(serverSideUri: String, clientProbe: TestProbe): Unit = {
      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest].uri === Uri(serverSideUri)
      serverHandler.reply(HttpResponse(entity = "ok"))
      clientProbe.expectMsgType[HttpResponse].entity === HttpEntity("ok")
    }

    def closeHostConnector(hostConnector: ActorRef): Unit = {
      val probe = TestProbe()
      probe.send(hostConnector, Http.CloseAll)
      probe.expectMsg(Http.ClosedAll)
    }
  }
}
