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

package spray.can.client

import org.specs2.mutable.Specification
import com.typesafe.config.{ConfigFactory, Config}
import akka.actor.{ActorRef, Status, ActorSystem}
import akka.io.IO
import akka.testkit.TestProbe
import spray.can.{HostConnectorInfo, HostConnectorSetup, Http}
import spray.testkit.TestUtils._
import spray.httpx.RequestBuilding._
import spray.http._
import HttpHeaders._

class SprayCanClientSpec extends Specification {

  val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
      io.tcp.trace-logging = off
    }""")
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)


  "The request-level client infrastructure" should {

    "properly complete a simple request/response cycle with a request containing a host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc") ~> Host(hostname, port))
      verifyServerSideRequestAndReply("/abc", probe)
    }

    "transform absolute request URIs into relative URIs plus host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get(s"http://$hostname:$port/abc"))
      verifyServerSideRequestAndReply("/abc", probe)
    }

    "produce an error if the request does not contain a Host-header or an absolute URI" in {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc"))
      probe.expectMsgType[Status.Failure].cause.getMessage must startWith("Cannot establish effective request URI")
    }
  }

  "The host-level client infrastructure" should {

    "return the same HostConnector for identical setup requests" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector, _) = probe.expectMsgType[HostConnectorInfo]
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      probe.sender === hostConnector
    }

    "properly complete a simple request/response cycle with a Host-header request" in new TestSetup {
      val probe = sendViaHostConnector(Get("/abc") ~> Host(hostname, port))
      verifyServerSideRequestAndReply("/abc", probe)
    }

    "add a host header to the request if it doesn't contain one" in new TestSetup {
      val probe = sendViaHostConnector(Get("/abc"))
      verifyServerSideRequestAndReply("/abc", probe)
    }

    "accept absolute URIs and render them unchanged" in new TestSetup {
      val probe = sendViaHostConnector(Get("http://www.example.com/"))
      verifyServerSideRequestAndReply("http://www.example.com/", probe)
    }

    "properly react to Http.Close commands" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector, _) = probe.expectMsgType[HostConnectorInfo]

      // open two connections
      val clientA = send(hostConnector, Get("/a"))
      val clientB = send(hostConnector, Get("/b"))
      val serverA = acceptConnection()
      val serverB = acceptConnection()

      probe.send(hostConnector, Http.Close)
      clientA.expectMsgType[Status.Failure].cause.getMessage == "Connection actively closed"
      clientB.expectMsgType[Status.Failure].cause.getMessage == "Connection actively closed"
      serverA.expectMsgType[HttpRequest]
      serverA expectMsg Http.PeerClosed
      serverB.expectMsgType[HttpRequest]
      serverB expectMsg Http.PeerClosed
      probe expectMsg Http.Closed
    }
  }

  step(system.shutdown())

  class TestSetup extends org.specs2.specification.Scope {
    val (hostname, port) = temporyServerHostnameAndPort()
    val bindHandler = TestProbe()

    // automatically bind a server
    val listener = {
      val commander = TestProbe()
      commander.send(IO(Http), Http.Bind(bindHandler.ref, hostname, port))
      commander expectMsg Http.Bound
      commander.sender
    }

    def acceptConnection(): TestProbe = {
      bindHandler.expectMsgType[Http.Connected]
      val probe = TestProbe()
      bindHandler reply Http.Register(probe.ref)
      probe
    }

    def sendViaHostConnector(request: HttpRequest): TestProbe = {
      val probe = TestProbe()
      probe.send(IO(Http), HostConnectorSetup(hostname, port))
      val HostConnectorInfo(hostConnector, _) = probe.expectMsgType[HostConnectorInfo]
      probe.sender === hostConnector
      probe reply request
      probe
    }

    def send(connector: ActorRef, request: HttpRequest): TestProbe = {
      val probe = TestProbe()
      probe.send(connector, request)
      probe
    }

    def verifyServerSideRequestAndReply(serverSideUri: String, clientProbe: TestProbe): Unit = {
      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest].uri === Uri(serverSideUri)
      serverHandler reply HttpResponse(entity = "ok")

      clientProbe.expectMsgType[HttpResponse].entity === HttpEntity("ok")
    }
  }
}
