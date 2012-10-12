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
package spray.io

import org.specs2.mutable.Specification
import annotation.tailrec
import javax.net.ssl._
import java.io.{BufferedWriter, OutputStreamWriter, InputStreamReader, BufferedReader}
import scala.Boolean
import akka.pattern.ask
import spray.util._
import java.nio.ByteBuffer
import akka.util.{Duration, Timeout}
import spray.io._
import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import java.security.{KeyStore, SecureRandom}


class SslTlsSupportSpec extends Specification {
  implicit val system = ActorSystem()
  implicit val sslContext = createSslContext("/ssl-test-keystore.jks", "")
  def log = system.log
  val port = 23454
  val serverThread = new ServerThread
  serverThread.start()
  val ioBridge = new IOBridge(system, ConfigFactory.parseString("spray.io.confirm-sends = off")).start()

  sequential

  "The SslTlsSupportSpec" should {
    "have a working SSLSocket client/server infrastructure" in {
      socketSendReceive("1+2") === "3"
      socketSendReceive("12+24") === "36"
    }
  }

  "The SslTlsSupport" should {
    implicit val timeOut: Timeout = Duration("1 s")
    "be able to complete a simple request/response dialog from the client-side" in {
      import IOClient._
      val Connected(handle) = system.actorOf(Props(new SslClientActor), "ssl-client")
        .ask(Connect("localhost", port)).await
      val Received(_, buf) = handle.handler.ask(Send(ByteBuffer.wrap("3+4\n".getBytes))).await
      handle.handler ! IOClient.Close(CleanClose)
      buf.drainToString === "7\n"
    }
    "be able to complete a simple request/response dialog from the server-side" in {
      import IOServer._
      system.actorOf(Props(new SslServerActor), "ssl-server").ask(Bind("localhost", port + 1)).await
      socketSendReceive("20+6", port + 1) === "26"
    }
  }

  "The SslTlsSupportSpec" should {
    "shut down cleanly" in {
      socketSendReceive("EXIT") === "OK"
    }
  }

  step {
    system.shutdown()
    ioBridge.stop()
  }

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

  def socketSendReceive(send: String, _port: Int = port): String = {
    val socketFactory = sslContext.getSocketFactory
    val socket = socketFactory.createSocket("localhost", _port).asInstanceOf[SSLSocket]
    val (reader, writer) = readerAndWriter(socket)
    writer.write(send + "\n")
    writer.flush()
    log.debug("Client sent: {}", send)
    val string = reader.readLine()
    log.debug("Client received: {}", string)
    socket.close()
    string
  }

  def readerAndWriter(socket: SSLSocket) = {
    val reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
    val writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
    reader -> writer
  }

  class SslClientActor extends IOClient(ioBridge) with ConnectionActors {
    protected def pipeline = frontEnd >> SslTlsSupport(ClientSSLEngineProvider.default, log)
    def frontEnd = new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) = new Pipelines {
        var receiver: ActorRef = _
        val commandPipeline: CPL = {
          case x: IOClient.Send => receiver = context.sender; commandPL(x)
          case cmd => commandPL(cmd)
        }
        val eventPipeline: EPL = {
          case x: IOClient.Received => receiver ! x
          case ev => eventPL(ev)
        }
      }
    }
  }

  class SslServerActor extends IOServer(ioBridge) with ConnectionActors {
    protected def pipeline = frontEnd >> SslTlsSupport(ServerSSLEngineProvider.default, log)
    def frontEnd: PipelineStage = new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val commandPipeline = commandPL
          val eventPipeline: EPL = {
            case IOServer.Received(_, buf) =>
              val input = buf.drainToString.dropRight(1)
              log.debug("Server received: {}", input)
              val response = serverResponse(input)
              commandPL(IOServer.Send(ByteBuffer.wrap(response.getBytes)))
              log.debug("Server sent: {}", response.dropRight(1))
            case ev => eventPL(ev)
          }
        }
    }
  }

  class ServerThread extends Thread {
    override def run() {
      val socketFactory = sslContext.getServerSocketFactory
      val serverSocket = socketFactory.createServerSocket(port).asInstanceOf[SSLServerSocket]
      @tailrec def serverLoop() {
        val socket = serverSocket.accept().asInstanceOf[SSLSocket]
        val (reader, writer) = readerAndWriter(socket)
        @tailrec def connectionLoop(): Boolean = {
          val s = reader.readLine()
          log.debug("Server received: {}", s)
          s match {
            case null => true
            case "EXIT" =>
              writer.write("OK\n")
              writer.flush()
              log.debug("Server sent: OK")
              false
            case string =>
              val result = serverResponse(string)
              writer.write(result)
              writer.flush()
              log.debug("Server sent: {}", result.dropRight(1))
              connectionLoop()
          }
        }
        if (try connectionLoop() finally socket.close()) serverLoop()
      }
      serverLoop()
      serverSocket.close()
    }
  }

  def serverResponse(input: String): String = input.split('+').map(_.toInt).reduceLeft(_ + _).toString + '\n'
}
