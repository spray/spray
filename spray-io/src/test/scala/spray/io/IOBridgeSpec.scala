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

import akka.pattern.ask
import java.nio.ByteBuffer
import akka.util.{Timeout, Duration}
import akka.actor._
import akka.dispatch.Future
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import spray.util._
import ConnectionCloseReasons._


class IOBridgeSpec extends Specification {
  implicit val timeout: Timeout = Duration("500 ms")
  implicit val system = ActorSystem("IOBridgeSpec")
  val port = 23456

  val bridge = IOExtension(system).ioBridge
  val server = system.actorOf(Props(new TestServer(bridge)), name = "test-server")
  val client = system.actorOf(Props(new TestClient(bridge)), name = "test-client")

  sequential

  "An IOBridge" should {
    "properly bind a test server" in {
      (server ? IOServer.Bind("localhost", port)).await must beAnInstanceOf[IOServer.Bound]
    }
    "properly complete a one-request dialog" in {
      request("Echoooo").await === ("Echoooo" -> CleanClose)
    }
    "properly complete 100 requests in parallel" in {
      val requests = Future.traverse((1 to 100).toList) { i => request("Ping" + i).map(r => i -> r._1) }
      val beOk: Matcher[(Int, String)] = ({ t:(Int, String) => t._2 == "Ping" + t._1 }, "not ok")
      requests.await must beOk.forall
    }
    "support confirmed connection closing" in {
      request("Yeah", ConfirmedClose).await === ("Yeah" -> ConfirmedClose)
    }
  }

  step { system.shutdown() }

  class TestServer(_rootIoBridge: ActorRef) extends IOServer(_rootIoBridge) {
    override def receive = super.receive orElse {
      case IOBridge.Received(handle, buffer) => sender ! IOBridge.Send(handle, buffer)
    }
  }

  class TestClient(_rootIoBridge: ActorRef) extends IOClient(_rootIoBridge) {
    var requests = Map.empty[Handle, ActorRef]
    override def receive: Receive = myReceive orElse super.receive
    def myReceive: Receive = {
      case (handle: Handle, string: String) =>
        requests += handle -> sender
        handle.ioBridge ! IOBridge.Send(handle, ByteBuffer.wrap(string.getBytes))
      case IOBridge.Received(handle, buffer) =>
        requests(handle) ! buffer.drainToString
      case cmd@IOBridge.Close(handle, _) =>
        requests += handle -> sender
        handle.ioBridge ! cmd
      case IOBridge.Closed(handle, reason) =>
        requests(handle) ! reason
        requests -= handle
    }
  }

  def request(payload: String, closeReason: CloseCommandReason = CleanClose) = {
    for {
      IOClient.Connected(handle) <- (client ? IOClient.Connect("localhost", port)).mapTo[IOClient.Connected]
      response: String           <- (client ? (handle -> payload)).mapTo[String]
      reason: ClosedEventReason  <- (client ? IOBridge.Close(handle, closeReason)).mapTo[ClosedEventReason]
    } yield response -> reason
  }
}
