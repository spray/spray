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

package spray.can.server

import org.specs2.mutable.Specification
import com.typesafe.config.{ConfigFactory, Config}
import akka.actor.{ActorRef, ActorSystem}
import akka.io.IO
import akka.testkit.TestProbe
import spray.can.Http
import spray.testkit.TestUtils._
import spray.httpx.RequestBuilding._
import spray.http._
import HttpHeaders._

class SprayCanServerSpec extends Specification {

  val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
      io.tcp.trace-logging = off
    }""")
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)


  "The server-side spray-can HTTP infrastructure" should {

    "properly bind and unbind an HttpListener" in new TestSetup {
      val commander = TestProbe()
      commander.send(listener, Http.Unbind)
      commander expectMsg Http.Unbound
    }

    "properly complete a simple request/response cycle" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probe = sendRequest(connection, Get("/abc"))
      serverHandler.expectMsgType[HttpRequest].uri === Uri("/abc")

      serverHandler reply HttpResponse(entity = "yeah")
      probe.expectMsgType[HttpResponse].entity === HttpEntity("yeah")
    }

    "maintain response order for pipelined requests" in new TestSetup {
      val connection = openNewClientConnection()
      val serverHandler = acceptConnection()

      val probeA = sendRequest(connection, Get("/a"))
      val probeB = sendRequest(connection, Get("/b"))
      serverHandler.expectMsgType[HttpRequest].uri === Uri("/a")
      val responderA = serverHandler.sender
      serverHandler.expectMsgType[HttpRequest].uri === Uri("/b")
      serverHandler reply HttpResponse(entity = "B")
      serverHandler.send(responderA, HttpResponse(entity = "A"))

      probeA.expectMsgType[HttpResponse].entity === HttpEntity("A")
      probeB.expectMsgType[HttpResponse].entity === HttpEntity("B")
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

    def openNewClientConnection(): ActorRef = {
      val probe = TestProbe()
      probe.send(IO(Http), Http.Connect(hostname, port))
      probe.expectMsgType[Http.Connected]
      probe.sender
    }

    def acceptConnection(): TestProbe = {
      bindHandler.expectMsgType[Http.Connected]
      val probe = TestProbe()
      bindHandler reply Http.Register(probe.ref)
      probe
    }

    def sendRequest(connection: ActorRef, part: HttpRequestPart): TestProbe = {
      val probe = TestProbe()
      probe.send(connection, part)
      probe
    }
  }
}
