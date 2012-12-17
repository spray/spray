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

import java.nio.ByteBuffer
import scala.concurrent.duration.Duration
import org.specs2.mutable.Specification
import akka.actor.{ActorRef, Props, ActorSystem}
import akka.util.Timeout
import akka.pattern.ask
import spray.util._


class ConnectionActorSupervisionSpec extends Specification  {
  implicit val timeout: Timeout = Duration(1, "sec") // for asks below
  implicit val system = ActorSystem()
  val port = 23556
  val bridge = IOExtension(system).ioBridge()
  val server = system.actorOf(Props(new TestServer(bridge)), name = "test-server")
  val client = system.actorOf(Props(new TestClient(bridge)), name = "test-client")

  installDebuggingEventStreamLoggers()

  sequential

  step(server.ask(IOServer.Bind("localhost", port, tag = LogMark("SERVER"))).await)

  "Connection Actors" should {
    var connection: Connection = null
    "be able to run their pipeline normally" in {
      connection = client.ask(IOClient.Connect("localhost", port, tag = LogMark("CLIENT")))
        .mapTo[IOClient.Connected].await.connection
      client.ask(connection -> "ECHO").await === "ECHO"
    }
    "be stopped and their connection closed in case of pipeline exceptions" in {
      client.ask(connection -> "CRASH").await === ConnectionCloseReasons.PeerClosed
    }
  }

  step(system.shutdown())

  class TestServer(_rootIoBridge: ActorRef) extends IOServer(_rootIoBridge) with ConnectionActors {
    def pipeline = new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL) = new Pipelines {
        val commandPipeline = commandPL
        val eventPipeline: EPL = {
          case IOServer.Received(connection, buffer) if buffer.duplicate.drainToString == "CRASH" =>
            sys.error("Crash Boom Bang!")
          case IOServer.Received(connection, buffer) =>
            commandPipeline(IOServer.Send(buffer))
        }
      }
    }
  }

  class TestClient(_rootIoBridge: ActorRef) extends IOClient(_rootIoBridge) {
    var savedSender: ActorRef = _
    override def receive: Receive = myReceive orElse super.receive
    def myReceive: Receive = {
      case (connection: Connection, string: String) =>
        savedSender = sender
        connection.ioBridge ! IOBridge.Send(connection, ByteBuffer.wrap(string.getBytes))
      case IOPeer.Received(_, buffer) => savedSender ! buffer.drainToString
      case IOPeer.Closed(connection, reason) => savedSender ! reason
    }
  }
}
