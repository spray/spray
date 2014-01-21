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

package spray.can.client

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import com.typesafe.config.{ ConfigValueFactory, ConfigFactory, Config }
import scala.concurrent.duration._
import akka.actor.{ ActorRef, Status, ActorSystem }
import akka.io.IO
import akka.testkit.TestProbe
import spray.can.Http
import spray.can.Http.{ RegisterChunkHandler, ClientConnectionType }
import spray.io.ClientSSLEngineProvider
import spray.util.Utils._
import spray.httpx.RequestBuilding._
import spray.http._
import HttpHeaders._
import StatusCodes._

class SprayCanClientSpec extends Specification with NoTimeConversions {

  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = ERROR
    akka.io.tcp.trace-logging = off
    spray.can.client.request-timeout = 500ms
    spray.can.client.response-chunk-aggregation-limit = 0
    spray.can.host-connector.max-retries = 1
    spray.can.host-connector.idle-timeout = infinite
    spray.can.host-connector.client.request-timeout = 500ms
    spray.can.server.pipelining-limit = 4
    spray.can.server.verbose-error-messages = on
    spray.can.server.request-chunk-aggregation-limit = 0
    spray.can.server.transparent-head-requests = off""")
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
      client.send(clientConnection, ChunkedMessageEnd)
      client.send(clientConnection, Get("/def") ~> Host(hostname, port))

      val server = acceptConnection()
      val chunkHandler = TestProbe()

      server.expectMsgType[ChunkedRequestStart].request.uri.path.toString === "/abc"
      server.reply(RegisterChunkHandler(chunkHandler.ref))

      chunkHandler.expectMsg(MessageChunk("123"))
      chunkHandler.expectMsg(MessageChunk("456"))
      chunkHandler.expectMsg(ChunkedMessageEnd)
      val firstRequestSender = server.sender()
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

    "not produce a request timeout if the initial chunk of a streamed response arrives on time" in new TestSetup {
      val clientConnection = newClientConnect()
      val request = Get("/abc") ~> Host(hostname, port)
      val client = send(clientConnection, request)
      val server = acceptConnection()
      server.expectMsgType[HttpRequest].uri.path.toString === "/abc"
      server.reply(ChunkedResponseStart(HttpResponse(entity = "ok-abc")))
      client.expectMsgType[ChunkedResponseStart]
      client.expectMsg(MessageChunk("ok-abc"))
      client.expectNoMsg(1000.millis) // configured timeout is 500ms
      client.send(clientConnection, Http.Close)
      client.expectMsg(Http.Closed)
      server.expectMsg(Http.PeerClosed)
      unbind()
    }
  }

  "The host-level client infrastructure" should {
    "return the same HostConnector for identical setup requests" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port))
      val Http.HostConnectorInfo(hostConnector1, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port))
      val Http.HostConnectorInfo(hostConnector2, _) = probe.expectMsgType[Http.HostConnectorInfo]
      hostConnector1 === hostConnector2
    }

    "return the different HostConnectors for setup requests with differing hostnames" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup("www.spray.io"))
      val Http.HostConnectorInfo(hostConnector1, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.send(IO(Http), Http.HostConnectorSetup("spray.io"))
      val Http.HostConnectorInfo(hostConnector2, _) = probe.expectMsgType[Http.HostConnectorInfo]
      hostConnector1 !== hostConnector2
    }

    "properly complete a simple request/response cycle with a Host-header request" in new TestSetup {
      val (probe, hostConnector) = sendViaHostConnector(Get("/hij") ~> Host(hostname, port) ~> Date(DateTime.now))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/hij", probe)
      closeHostConnector(hostConnector)
    }

    "add a host header to the request if it doesn't contain one" in new TestSetup {
      val (probe, hostConnector) = sendViaHostConnector(Get("/lmn"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/lmn", probe)
      closeHostConnector(hostConnector)
    }

    "add default headers to requests if they don't contain them" in new TestSetup {
      val probe = TestProbe()
      val defaultHeader = RawHeader("X-Custom-Header", "Default")
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, defaultHeaders = List(defaultHeader)))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      send(hostConnector, Get("/pqr"))
      acceptConnection().expectMsgType[HttpRequest].headers.find(_.name == "X-Custom-Header") === Some(defaultHeader)
      send(hostConnector, Get("/pqr") ~> RawHeader("X-Custom-Header", "Customized!"))
      acceptConnection().expectMsgType[HttpRequest].headers.find(_.name == "X-Custom-Header").get.value === "Customized!"
      closeHostConnector(hostConnector)
    }

    "accept absolute URIs and render them unchanged" in new TestSetup {
      val uri = s"http://$hostname:$port/foo"
      val (probe, hostConnector) = sendViaHostConnector(Get(uri))
      verifyServerSideRequestAndReply(uri, probe)
      closeHostConnector(hostConnector)
    }

    "support a clean CloseAll shutdown" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]

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

    "support usage of custom SSLEngines" in new TestSetup {
      @volatile var customProviderUsed = false
      implicit val customEngineProvider = ClientSSLEngineProvider.fromFunc { pc ⇒
        customProviderUsed = true
        val default = ClientSSLEngineProvider.default
        default(pc)
      }

      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, sslEncryption = true))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.sender() === hostConnector
      probe.reply(Get("/"))
      probe.expectMsgType[Status.Failure].cause.getMessage == "Connection actively closed"

      customProviderUsed === true

      closeHostConnector(hostConnector)
    }

    "use a configured proxy" in new TestSetup {
      val (probe, hostConnector) = sendViaProxiedConnector("example.com", 8080,
        settingsConf = proxyConf(hostname, port))
      probe.reply(Get("/foo"))
      verifyServerSideRequestAndReply("http://example.com:8080/foo", probe)
      closeHostConnector(hostConnector)
    }

    "ignore configured non-proxy-hosts" in new TestSetup {
      val (probe, hostConnector) = sendViaProxiedConnector(hostname, port,
        settingsConf = proxyConf("proxy.com", 9999, List(hostname)))
      probe.reply(Get("/foo"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/foo", probe)
      closeHostConnector(hostConnector)
    }

    "use a proxy specified via 'ConnectionType.Proxied'" in new TestSetup {
      val (probe, hostConnector) = sendViaProxiedConnector("example.com", 8080,
        connectionType = ClientConnectionType.Proxied(hostname, port))
      probe.reply(Get("/foo"))
      verifyServerSideRequestAndReply("http://example.com:8080/foo", probe)
      closeHostConnector(hostConnector)
    }

    "directly access hosts with ConnectionType.Direct" in new TestSetup {
      val (probe, hostConnector) = sendViaProxiedConnector(hostname, port,
        settingsConf = proxyConf("proxy.com", 9999),
        connectionType = ClientConnectionType.Direct)
      probe.reply(Get("/foo"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/foo", probe)
      closeHostConnector(hostConnector)
    }

    "correctly reuse proxied connectors" in new TestSetup {
      val probe = TestProbe()
      val proxied = ClientConnectionType.Proxied(hostname, port)
      probe.send(IO(Http), Http.HostConnectorSetup("example.com", 8080, connectionType = proxied))
      val Http.HostConnectorInfo(hostConnector1, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.send(IO(Http), Http.HostConnectorSetup("example.com", 8080, connectionType = proxied))
      val Http.HostConnectorInfo(hostConnector2, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.send(IO(Http), Http.HostConnectorSetup("domain.net", 8080, connectionType = proxied))
      val Http.HostConnectorInfo(hostConnector3, _) = probe.expectMsgType[Http.HostConnectorInfo]
      hostConnector1 === hostConnector2
      hostConnector2 !== hostConnector3
    }

    "returns 3xx HttpResponse when follow-redirects is disabled" in new TestSetup {
      val request = Get("/def") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest]
      serverHandler.reply(HttpResponse(status = TemporaryRedirect, headers = Location("/go-here") :: Nil))

      val r = probe.expectMsgType[HttpResponse]
      r.status === TemporaryRedirect
      r.header[Location].head.value === "/go-here"

      closeHostConnector(hostConnector)
    }

    "perform a redirect when max-redirects is > 0" in new TestSetup {
      val redirectConf = system.settings.config withValue
        ("spray.can.host-connector.max-redirects", ConfigValueFactory.fromAnyRef(5))

      val request = Get("/def") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(HostConnectorSettings(redirectConf))))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest]
      serverHandler.reply(HttpResponse(status = TemporaryRedirect, headers = Location("/go-here") :: Nil))

      val serverHandler2 = acceptConnection()
      val req = serverHandler2.expectMsgType[HttpRequest]
      req.method === HttpMethods.GET
      req.uri.toString === s"http://$hostname:$port/go-here"

      serverHandler2.reply(HttpResponse(entity = "ok"))
      val r = probe.expectMsgType[HttpResponse]
      r.entity === HttpEntity("ok")

      closeHostConnector(hostConnector)
    }

    "only follow one redirect when max-redirects is set to 1" in new TestSetup {
      val redirectConf = system.settings.config withValue
        ("spray.can.host-connector.max-redirects", ConfigValueFactory.fromAnyRef(1))

      val request = Get("/def") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(HostConnectorSettings(redirectConf))))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest]
      serverHandler.reply(HttpResponse(status = TemporaryRedirect, headers = Location("/go-here") :: Nil))

      val serverHandler2 = acceptConnection()
      val req = serverHandler2.expectMsgType[HttpRequest]
      req.method === HttpMethods.GET
      req.uri.toString === s"http://$hostname:$port/go-here"
      serverHandler2.reply(HttpResponse(status = TemporaryRedirect, headers = Location("/now-go-here") :: Nil))

      val r = probe.expectMsgType[HttpResponse]
      r.status === TemporaryRedirect
      r.header[Location] === Some(Location("/now-go-here"))

      closeHostConnector(hostConnector)
    }

    "follow 302 redirect for a POST request with a GET request" in new TestSetup {
      val redirectConf = system.settings.config withValue
        ("spray.can.host-connector.max-redirects", ConfigValueFactory.fromAnyRef(5))

      val request = Post("/def") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(HostConnectorSettings(redirectConf))))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest]
      serverHandler.reply(HttpResponse(status = Found, headers = Location("/go-here") :: Nil))

      val serverHandler2 = acceptConnection()
      val req = serverHandler2.expectMsgType[HttpRequest]
      req.method === HttpMethods.GET
      req.uri.toString === s"http://$hostname:$port/go-here"

      serverHandler2.reply(HttpResponse(entity = "ok"))
      val r = probe.expectMsgType[HttpResponse]
      r.entity === HttpEntity("ok")

      closeHostConnector(hostConnector)
    }

    "follow 302 redirect for a HEAD request with a HEAD request" in new TestSetup {
      val redirectConf = system.settings.config withValue
        ("spray.can.host-connector.max-redirects", ConfigValueFactory.fromAnyRef(5))

      val request = Head("/head") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(HostConnectorSettings(redirectConf))))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      val firstReq = serverHandler.expectMsgType[HttpRequest]
      firstReq.method === HttpMethods.HEAD
      serverHandler.reply(HttpResponse(status = Found, headers = Location("/go-here") :: Nil))

      val serverHandler2 = acceptConnection()
      val req = serverHandler2.expectMsgType[HttpRequest]
      req.method === HttpMethods.HEAD
      req.uri.toString === s"http://$hostname:$port/go-here"

      serverHandler2.reply(HttpResponse(status = OK))
      val r = probe.expectMsgType[HttpResponse]
      r.status === OK

      closeHostConnector(hostConnector)
    }

    "not follow 301 redirect for a POST request as this requires user confirmation" in new TestSetup {
      val redirectConf = system.settings.config withValue
        ("spray.can.host-connector.max-redirects", ConfigValueFactory.fromAnyRef(5))

      val request = Post("/def") ~> Host(hostname, port)
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(HostConnectorSettings(redirectConf))))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.reply(request)

      val serverHandler = acceptConnection()
      serverHandler.expectMsgType[HttpRequest]
      serverHandler.reply(HttpResponse(status = MovedPermanently, headers = Location("/go-here") :: Nil))

      val r = probe.expectMsgType[HttpResponse]
      r.status === MovedPermanently
      r.header[Location] === Some(Location("/go-here"))

      closeHostConnector(hostConnector)
    }
  }

  "The request-level client infrastructure" should {
    "properly complete a simple request/response cycle with a request containing a host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc") ~> Host(hostname, port))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/abc", probe)
    }

    "transform absolute request URIs into relative URIs plus host header" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get(s"http://$hostname:$port/abc?query#fragment"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/abc?query", probe)
    }

    "support absolute request URIs without path component" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get(s"http://$hostname:$port"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/", probe)
    }

    "produce an error if the request does not contain a Host-header or an absolute URI" in {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc"))
      probe.expectMsgType[Status.Failure].cause.getMessage must startWith("Cannot establish effective request URI")
    }

    "produce an error if the request was not completed within the configured timeout" in new TestSetup {
      val probe = TestProbe()
      probe.send(IO(Http), Get("/abc") ~> Host(hostname, port))
      acceptConnection()
      probe.expectMsgType[Status.Failure].cause must beAnInstanceOf[Http.RequestTimeoutException]
    }

    "use a configured proxy" in new TestSetup {
      val conf = proxyConf(hostname, port).withFallback(testConf)
      implicit val system = ActorSystem(actorSystemNameFrom(getClass), conf)
      val probe = TestProbe()
      probe.send(IO(Http), Get("http://example.com:9999/xyz"))
      verifyServerSideRequestAndReply("http://example.com:9999/xyz", probe)
    }

    "ignore configured non-proxy-hosts" in new TestSetup {
      val conf = proxyConf("proxy.org", 9999, List(hostname)).withFallback(testConf)
      implicit val system = ActorSystem(actorSystemNameFrom(getClass), conf)
      val probe = TestProbe()
      probe.send(IO(Http), Get(s"http://$hostname:$port/xyz"))
      verifyServerSideRequestAndReply(s"http://$hostname:$port/xyz", probe)
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
      commander.sender()
    }

    def newClientConnect(): ActorRef = {
      val probe = TestProbe()
      probe.send(IO(Http), Http.Connect(hostname, port))
      probe.expectMsgType[Http.Connected]
      probe.sender()
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

    def sendViaHostConnector(request: HttpRequest)(implicit sslEngineProvider: ClientSSLEngineProvider): (TestProbe, ActorRef) = {
      val probe = TestProbe()
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.sender() === hostConnector
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

    def proxyConf(proxyHost: String, proxyPort: Int, ignore: List[String] = Nil) =
      ConfigFactory.parseString(s"""
        spray.can.client.proxy.http {
        host = "$proxyHost"
        port = $proxyPort
        non-proxy-hosts = [${ignore.map('"' + _ + '"').mkString(", ")}]}""")

    def sendViaProxiedConnector(hostname: String, port: Int, settingsConf: Config = testConf,
                                connectionType: ClientConnectionType = ClientConnectionType.AutoProxied): (TestProbe, ActorRef) = {
      val probe = TestProbe()
      val settings = HostConnectorSettings(settingsConf
        .withFallback(testConf)
        .withFallback(ConfigFactory.load()))
      probe.send(IO(Http), Http.HostConnectorSetup(hostname, port, settings = Some(settings), connectionType = connectionType))
      val Http.HostConnectorInfo(hostConnector, _) = probe.expectMsgType[Http.HostConnectorInfo]
      probe.sender() === hostConnector
      probe -> hostConnector
    }
  }
}
