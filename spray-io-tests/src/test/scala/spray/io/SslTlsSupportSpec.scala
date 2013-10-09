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
package spray.io

import java.io.{ BufferedWriter, OutputStreamWriter, InputStreamReader, BufferedReader }
import javax.net.ssl._
import java.net.InetSocketAddress
import java.security.{ KeyStore, SecureRandom }
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.config.{ ConfigFactory, Config }
import scala.concurrent.duration._
import scala.concurrent.Future
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor._
import akka.testkit.TestProbe
import akka.util.{ ByteString, Timeout }
import akka.io.{ IO, Tcp }
import spray.testkit.TestUtils
import spray.util._

class SslTlsSupportSpec extends Specification with NoTimeConversions {

  implicit val timeOut: Timeout = 1.second
  val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
      io.tcp.trace-logging = off
    }""")
  implicit val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)
  import system.dispatcher

  "The SslTlsSupport" should {

    "support a simple encrypted dialog between a Java client and a Java server (test infrastructure)" in new TestSetup {
      val server = new JavaSslServer
      val clientConn = newJavaSslClientConnection(server.address)
      val serverConn = server.acceptOne()

      inParallel(clientConn.writeLn("Foo"), serverConn.readLn()) === "Foo"
      inParallel(serverConn.writeLn("bar"), clientConn.readLn()) === "bar"
      inParallel(serverConn.writeLn("baz"), clientConn.readLn()) === "baz"
      inParallel(clientConn.writeLn("yeah"), serverConn.readLn()) === "yeah"

      val baselineSessionCounts = sessionCounts()
      clientConn.close()
      sessionCounts() === baselineSessionCounts
      serverConn.close()
      sessionCounts() === baselineSessionCounts
      server.close()
      sessionCounts() === baselineSessionCounts
    }

    "support a simple encrypted dialog between a spray client and a Java server" in new TestSetup {
      val server = new JavaSslServer
      val connAttempt = attemptSpraySslClientConnection(server.address)
      val serverConn = server.acceptOne()
      val clientConn = connAttempt.finishConnect()

      clientConn.writeLn("Foo")
      serverConn.readLn() === "Foo"
      serverConn.writeLn("bar")
      clientConn.events.expectMsg(Tcp.Received(ByteString("bar\n")))
      serverConn.writeLn("baz")
      clientConn.events.expectMsg(Tcp.Received(ByteString("baz\n")))
      clientConn.writeLn("yeah")
      serverConn.readLn() === "yeah"

      val baselineSessionCounts = sessionCounts()
      serverConn.close()
      sessionCounts() === baselineSessionCounts
      clientConn.events.expectMsg(Tcp.PeerClosed)
      server.close()
      sessionCounts() === baselineSessionCounts
      TestUtils.verifyActorTermination(clientConn.handler)
    }

    "support a simple encrypted dialog between a Java client and a spray server" in new TestSetup {
      val server = new SpraySslServer
      val clientConn = newJavaSslClientConnection(server.address)
      val serverConn = server.acceptOne()

      clientConn.writeLn("Foo")
      serverConn.events.expectMsg(Tcp.Received(ByteString("Foo\n")))
      serverConn.writeLn("bar")
      clientConn.readLn() === "bar"
      serverConn.writeLn("baz")
      clientConn.readLn() === "baz"
      clientConn.writeLn("yeah")
      serverConn.events.expectMsg(Tcp.Received(ByteString("yeah\n")))

      val baselineSessionCounts = sessionCounts()
      clientConn.close()
      sessionCounts() === baselineSessionCounts
      serverConn.events.expectMsg(Tcp.PeerClosed)
      TestUtils.verifyActorTermination(serverConn.handler)
      server.close()
      sessionCounts() === baselineSessionCounts
    }

    "support a simple encrypted dialog between a spray client and a spray server" in new TestSetup {
      val server = new SpraySslServer
      val connAttempt = attemptSpraySslClientConnection(server.address)
      val serverConn = server.acceptOne()
      val clientConn = connAttempt.finishConnect()

      clientConn.writeLn("Foo")
      serverConn.events.expectMsg(Tcp.Received(ByteString("Foo\n")))
      serverConn.writeLn("bar")
      clientConn.events.expectMsg(Tcp.Received(ByteString("bar\n")))
      serverConn.writeLn("baz")
      clientConn.events.expectMsg(Tcp.Received(ByteString("baz\n")))
      clientConn.writeLn("yeah")
      serverConn.events.expectMsg(Tcp.Received(ByteString("yeah\n")))

      val baselineSessionCounts = sessionCounts()
      clientConn.command(Tcp.Close)
      serverConn.events.expectMsg(Tcp.PeerClosed)
      TestUtils.verifyActorTermination(serverConn.handler)
      sessionCounts() === baselineSessionCounts
      clientConn.events.expectMsg(Tcp.Closed)
      TestUtils.verifyActorTermination(clientConn.handler)
      server.close()
      sessionCounts() === baselineSessionCounts
    }

    "correctly handle a ConfirmedClose command" in new TestSetup {
      val server = new JavaSslServer
      val connAttempt = attemptSpraySslClientConnection(server.address)
      val serverConn = server.acceptOne()
      val clientConn = connAttempt.finishConnect()

      clientConn.writeLn("Foo")
      serverConn.readLn() === "Foo"
      serverConn.writeLn("bar")
      clientConn.events.expectMsg(Tcp.Received(ByteString("bar\n")))

      clientConn.command(Tcp.ConfirmedClosed)
      clientConn.events.expectMsg(Tcp.ConfirmedClosed)
      TestUtils.verifyActorTermination(clientConn.handler)
      serverConn.close()
      server.close()
    }

    "produce a PeerClosed event upon receiving an SSL-level termination sequence" in new TestSetup(blockClosedEvent = true) {
      val server = new JavaSslServer
      val connAttempt = attemptSpraySslClientConnection(server.address)
      val serverConn = server.acceptOne()
      val clientConn = connAttempt.finishConnect()

      clientConn.writeLn("Foo")
      serverConn.readLn() === "Foo"
      serverConn.writeLn("bar")
      clientConn.events.expectMsg(Tcp.Received(ByteString("bar\n")))

      serverConn.close()
      clientConn.events.expectMsg(Tcp.PeerClosed)
      TestUtils.verifyActorTermination(clientConn.handler)
      server.close()
    }
  }

  step { system.shutdown() }

  val counter = new AtomicInteger

  class TestSetup(publishSslSessionInfo: Boolean = false, blockClosedEvent: Boolean = false) extends org.specs2.specification.Scope {
    implicit val sslContext = createSSLContext("/ssl-test-keystore.jks", "")

    def sessionCounts() = (clientSessions().length, serverSessions().length)
    def clientSessions() = sessions(_.getServerSessionContext)
    def serverSessions() = sessions(_.getClientSessionContext)
    def invalidateSessions() = {
      clientSessions().foreach(_.invalidate())
      serverSessions().foreach(_.invalidate())
    }

    def inParallel(body1: ⇒ Unit, expr2: ⇒ String): String =
      Future.sequence(List(Future(body1), Future(expr2))).await.apply(1).asInstanceOf[String]

    def newJavaSslClientConnection(address: InetSocketAddress) =
      new JavaServerConnection(sslContext.getSocketFactory.createSocket(address.getHostName, address.getPort).asInstanceOf[SSLSocket])

    class JavaSslServer {
      val address = Utils.temporaryServerAddress()
      private val serverSocket = sslContext.getServerSocketFactory.createServerSocket(address.getPort).asInstanceOf[SSLServerSocket]
      def acceptOne() = new JavaServerConnection(serverSocket.accept().asInstanceOf[SSLSocket])
      def close(): Unit = serverSocket.close()
    }

    class JavaServerConnection(socket: SSLSocket) {
      private val reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
      private val writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
      def readLn() = reader.readLine()
      def writeLn(msg: String) = {
        writer.write(msg + '\n')
        writer.flush()
      }
      def close(): Unit = socket.close()
    }

    def attemptSpraySslClientConnection(address: InetSocketAddress) = {
      val probe = TestProbe()
      probe.send(IO(Tcp), Tcp.Connect(address))
      new SpraySslClientConnectionAttempt(probe)
    }

    class SpraySslClientConnectionAttempt(connectedProbe: TestProbe) {
      def finishConnect() = new SpraySslClientConnection(connectedProbe.expectMsgType[Tcp.Connected], connectedProbe.sender)
    }

    class SpraySslClientConnection(connected: Tcp.Connected, connection: ActorRef) {
      val events = TestProbe()
      val handler = system.actorOf(Props(new ConnectionActor[ClientSSLEngineProvider](events.ref, connection, connected)),
        "client" + counter.incrementAndGet())
      connection ! Tcp.Register(handler, keepOpenOnPeerClosed = true)
      def command(msg: Any): Unit = handler ! msg
      def writeLn(msg: String) = command(Tcp.Write(ByteString(msg + '\n')))
    }

    class SpraySslServer {
      val address = Utils.temporaryServerAddress()
      private val bindProbe = TestProbe()
      private val acceptProbe = TestProbe()
      bindProbe.send(IO(Tcp), Tcp.Bind(acceptProbe.ref, address))
      bindProbe.expectMsgType[Tcp.Bound]
      def acceptOne() = new SpraySslServerConnection(acceptProbe.expectMsgType[Tcp.Connected], acceptProbe.sender)
      def close(): Unit = {
        bindProbe.reply(Tcp.Unbind)
        bindProbe.expectMsg(Tcp.Unbound)
      }
    }

    class SpraySslServerConnection(connected: Tcp.Connected, connection: ActorRef) {
      val events = TestProbe()
      val handler = system.actorOf(Props(new ConnectionActor[ServerSSLEngineProvider](events.ref, connection, connected)),
        "server" + counter.incrementAndGet())
      connection ! Tcp.Register(handler, keepOpenOnPeerClosed = true)
      def command(msg: Any): Unit = handler ! msg
      def writeLn(msg: String) = command(Tcp.Write(ByteString(msg + '\n')))
    }

    class ConnectionActor[T <: (PipelineContext ⇒ Option[SSLEngine])](events: ActorRef, connection: ActorRef,
                                                                      connected: Tcp.Connected)(implicit engineProvider: T) extends ConnectionHandler {
      val pipeline = frontend >> SslTlsSupport(publishSslSessionInfo) >> (filterPeerClosed ? blockClosedEvent)
      def receive = running(connection, pipeline, createSslTlsContext[T](connected))
      def frontend: PipelineStage = new PipelineStage {
        def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
          new Pipelines {
            val commandPipeline = commandPL
            val eventPipeline: EPL = {
              case ev ⇒
                events.tell(ev, context.sender)
                if (ev.isInstanceOf[Tcp.ConnectionClosed]) eventPL(ev)
            }
          }
      }
      def filterPeerClosed: PipelineStage = new PipelineStage {
        def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
          val commandPipeline = commandPL
          val eventPipeline: EPL = {
            case x: Tcp.ConnectionClosed ⇒ log.debug("Blocking ConnectionClosed event: " + x)
            case ev                      ⇒ eventPL(ev)
          }
        }
      }
    }

    private def sessions(f: SSLContext ⇒ SSLSessionContext): Seq[SSLSession] = {
      import collection.JavaConverters._
      val ctx = f(sslContext)
      ctx.getIds.asScala.toSeq.map(ctx.getSession)
    }
    private def createSSLContext(keyStoreResource: String, password: String): SSLContext = {
      val keyStore = KeyStore.getInstance("jks")
      keyStore.load(getClass.getResourceAsStream(keyStoreResource), password.toCharArray)
      val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(keyStore, password.toCharArray)
      val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
      trustManagerFactory.init(keyStore)
      val context = SSLContext.getInstance("SSL")
      context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
      context
    }
    private def createSslTlsContext[T <: (PipelineContext ⇒ Option[SSLEngine])](connected: Tcp.Connected)(implicit engineProvider: T, context: ActorContext): SslTlsContext =
      new SslTlsContext {
        def actorContext = context
        def remoteAddress = connected.remoteAddress
        def localAddress = connected.localAddress
        def log = LoggingContext.fromActorRefFactory(context)
        def sslEngine = engineProvider(this)
      }
  }
}
