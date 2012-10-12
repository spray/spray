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
import spray.util._
import org.specs2.mutable.Specification

class IOBridgeSpec extends Specification {
  implicit val timeout: Timeout = Duration("500 ms")
  implicit val system = ActorSystem("IOBridgeSpec")
  val port = 23456

  lazy val bridge = new IOBridge(system).start()
  lazy val server = system.actorOf(Props(new TestServer(bridge)), name = "test-server")
  lazy val client = system.actorOf(Props(new TestClient(bridge)), name = "test-client")

  sequential

  "An IOBridge" should {
    "properly bind a test server" in {
      (server ? IOServer.Bind("localhost", port)).await must beAnInstanceOf[IOServer.Bound]
    }
    "properly complete a one-request dialog" in {
      request("Echoooo").await === "Echoooo"
    }
    "properly complete 100 requests in parallel" in {
      val requests = Future.traverse((1 to 100).toList) { i => request("Ping" + i).map(i -> _) }
      val beOk: Matcher[(Int, String)] = ({ t:(Int, String) => t._2 == "Ping" + t._1 }, "not ok")
      requests.await must beOk.forall
    }
  }

  step {
    system.shutdown()
    bridge.stop()
  }

  class TestServer(ioBridge: IOBridge) extends IOServer(ioBridge) {
    override def receive = super.receive orElse {
      case IOBridge.Received(handle, buffer) => ioBridge ! IOBridge.Send(handle, buffer)
    }
  }

  class TestClient(ioBridge: IOBridge) extends IOClient(ioBridge) {
    var requests = Map.empty[Handle, ActorRef]
    override def receive = super.receive orElse {
      case (x: String, handle: Handle) =>
        requests += handle -> sender
        ioBridge ! IOBridge.Send(handle, ByteBuffer.wrap(x.getBytes))
      case IOBridge.Received(handle, buffer) =>
        requests(handle) ! new String(buffer.array, 0, buffer.limit)
    }
  }

  def request(payload: String) = {
    for {
      IOClient.Connected(handle) <- (client ? IOClient.Connect("localhost", port)).mapTo[IOClient.Connected]
      response <- (client ? (payload -> handle)).mapTo[String]
    } yield {
      bridge ! IOBridge.Close(handle, CleanClose)
      response
    }
  }

}
