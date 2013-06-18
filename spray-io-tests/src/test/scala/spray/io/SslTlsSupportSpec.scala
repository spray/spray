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
package spray.io

import java.io.{ BufferedWriter, OutputStreamWriter, InputStreamReader, BufferedReader }
import javax.net.ssl._
import java.net.{ InetSocketAddress, SocketException }
import java.security.{ KeyStore, SecureRandom }
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor._
import akka.event.{ Logging, LoggingAdapter }
import akka.testkit.TestProbe
import akka.util.{ ByteString, Timeout }
import akka.io.{ IO, Tcp }
import spray.testkit.TestUtils
import spray.util.{ Utils, LoggingContext }
import annotation.tailrec

class SslTlsSupportSpec extends Specification with NoTimeConversions {
  implicit val timeOut: Timeout = 1.second
  implicit val sslContext = createSslContext("/ssl-test-keystore.jks", "")
  val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
      io.tcp.trace-logging = off
    }""")
  implicit val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)

  "The SslTlsSupport" should {

    "work between a Java client and a Java server" in {
      val server = new JavaSslServer
      val client = new JavaSslClient(server.address)
      client.run()
      client.close()
      server.close()
    }

    "work between a spray client and a Java server" in {
      val server = new JavaSslServer
      val client = new SpraySslClient(server.address)
      client.run()
      client.close()
      server.close()
    }

    "work between a Java client and a spray server" in {
      val serverAddress = Utils.temporaryServerAddress()
      val bindHandler = system.actorOf(Props(new SpraySslServer))
      val probe = TestProbe()
      probe.send(IO(Tcp), Tcp.Bind(bindHandler, serverAddress))
      probe.expectMsgType[Tcp.Bound]

      val client = new JavaSslClient(serverAddress)
      client.run()
      client.close()
    }

    "work between a spray client and a spray server" in {
      val serverAddress = Utils.temporaryServerAddress()
      val bindHandler = system.actorOf(Props(new SpraySslServer))
      val probe = TestProbe()
      probe.send(IO(Tcp), Tcp.Bind(bindHandler, serverAddress))
      probe.expectMsgType[Tcp.Bound]

      val client = new SpraySslClient(serverAddress)
      client.run()
      client.close()
    }
  }

  step { system.shutdown() }

  val counter = new AtomicInteger

  def createSslContext(keyStoreResource: String, password: String): SSLContext = {
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

  class SpraySslClient(address: InetSocketAddress) {
    val probe = TestProbe()
    probe.send(IO(Tcp), Tcp.Connect(address))
    val connected = probe.expectMsgType[Tcp.Connected]
    val connection = probe.sender
    val handler = system.actorOf(
      props = Props {
        new ConnectionHandler {
          def receive = running(connection, frontend >> SslTlsSupport, sslTlsContext[ClientSSLEngineProvider](connected))
        }
      },
      name = "client" + counter.incrementAndGet())
    probe.send(connection, Tcp.Register(handler))

    def run(): Unit = {
      probe.send(handler, Tcp.Write(ByteString("3+4\n")))
      expectReceived(probe, ByteString("7\n"))
      probe.send(handler, Tcp.Write(ByteString("20+22\n")))
      expectReceived(probe, ByteString("42\n"))
    }

    def close(): Unit = {
      probe.send(handler, Tcp.Close)
      probe.expectMsgType[Tcp.ConnectionClosed]
      TestUtils.verifyActorTermination(handler)
    }

    // simple command/event frontend that dispatches incoming events to the sender of the last command
    def frontend: PipelineStage = new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var commander: ActorRef = _

          val commandPipeline: CPL = {
            case cmd ⇒
              commander = context.sender
              commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case ev: Tcp.Received if commander != null ⇒ commander ! ev
            case ev ⇒
              if (commander != null) commander ! ev
              eventPL(ev)
          }
        }
    }
  }

  class SpraySslServer extends Actor {
    def frontend: PipelineStage = new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var buffer = ByteString.empty

          val commandPipeline = commandPL
          val eventPipeline: EPL = {
            case Tcp.Received(data) ⇒
              @tailrec def consume(buffer: ByteString): ByteString = {
                val (next, rest) = buffer.span(_ != '\n'.toByte)
                if (rest.nonEmpty) {
                  val input = next.utf8String
                  context.log.debug("spray-io Server received {} from {}", input, sender)
                  val response = serverResponse(input)
                  commandPL(Tcp.Write(ByteString(response)))
                  context.log.debug("spray-io Server sent: {}", response.dropRight(1))

                  consume(rest.drop(1) /* \n */ )
                } else {
                  if (next.nonEmpty) context.log.debug(s"Buffering prefix of next message '$next'")
                  next
                }
              }

              buffer = consume(buffer ++ data)
            case ev ⇒ eventPL(ev)
          }
        }
    }
    def receive: Receive = {
      case x: Tcp.Connected ⇒
        val connection = sender
        val handler = system.actorOf(
          props = Props {
            new ConnectionHandler {
              def receive = running(connection, frontend >> SslTlsSupport, sslTlsContext[ServerSSLEngineProvider](x))
            }
          },
          name = "server" + counter.incrementAndGet())
        connection ! Tcp.Register(handler)
    }
  }

  class JavaSslServer extends Thread {
    val log: LoggingAdapter = Logging(system, getClass)
    val address = Utils.temporaryServerAddress()
    private val serverSocket =
      sslContext.getServerSocketFactory.createServerSocket(address.getPort).asInstanceOf[SSLServerSocket]
    @volatile private var socket: SSLSocket = _
    start()

    def close(): Unit = {
      serverSocket.close()
      if (socket != null) socket.close()
    }

    override def run(): Unit = {
      try {
        socket = serverSocket.accept().asInstanceOf[SSLSocket]
        val (reader, writer) = readerAndWriter(socket)
        while (true) {
          val line = reader.readLine()
          log.debug("SSLServerSocket Server received: {}", line)
          if (line == null) throw new SocketException("closed")
          val result = serverResponse(line)
          writer.write(result)
          writer.flush()
          log.debug("SSLServerSocket Server sent: {}", result.dropRight(1))
        }
      } catch {
        case _: SocketException ⇒ // expected during shutdown
      } finally close()
    }
  }

  class JavaSslClient(address: InetSocketAddress) {
    val socket = sslContext.getSocketFactory.createSocket(address.getHostName, address.getPort).asInstanceOf[SSLSocket]
    val (reader, writer) = readerAndWriter(socket)
    val log: LoggingAdapter = Logging(system, getClass)

    def run(): Unit = {
      write("1+2")
      readLine() === "3"
      write("12+24")
      readLine() === "36"
    }

    def write(string: String): Unit = {
      writer.write(string + "\n")
      writer.flush()
      log.debug("SSLSocket Client sent: {}", string)
    }

    def readLine() = {
      val string = reader.readLine()
      log.debug("SSLSocket Client received: {}", string)
      string
    }

    def close(): Unit = { socket.close() }
  }

  def readerAndWriter(socket: SSLSocket) = {
    val reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
    val writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
    reader -> writer
  }

  def serverResponse(input: String): String =
    try input.split('+').map(_.toInt).reduceLeft(_ + _).toString + '\n'
    catch {
      case e: Exception ⇒ e.printStackTrace(); "999\n"
    }

  def sslTlsContext[T <: (PipelineContext ⇒ Option[SSLEngine])](connected: Tcp.Connected)(implicit engineProvider: T, context: ActorContext): SslTlsContext =
    new SslTlsContext {
      def actorContext = context
      def remoteAddress = connected.remoteAddress
      def localAddress = connected.localAddress
      def log = implicitly[LoggingContext]
      def sslEngine = engineProvider(this)
    }

  @tailrec final def expectReceived(probe: TestProbe, expectedMessage: ByteString): Unit =
    if (expectedMessage.nonEmpty) {
      val data = probe.expectMsgType[Tcp.Received].data
      data must be_==(expectedMessage.take(data.length))
      expectReceived(probe, expectedMessage.drop(data.length))
    }
}
