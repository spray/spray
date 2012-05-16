/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.io

import akka.pattern.ask
import java.nio.ByteBuffer
import akka.util.{Timeout, Duration}
import akka.actor._
import akka.dispatch.Future
import org.specs2.matcher.Matcher
import cc.spray.util._
import org.specs2.mutable.Specification

class IoWorkerSpec extends Specification {
  implicit val timeout: Timeout = Duration("500 ms")
  implicit val system = ActorSystem("IoWorkerSpec")
  val port = 23456

  lazy val worker = new IoWorker(system).start()
  lazy val server = system.actorOf(Props(new TestServer(worker)), name = "test-server")
  lazy val client = system.actorOf(Props(new TestClient(worker)), name = "test-client")

  sequential

  "An IoWorker" should {
    "properly bind a test server" in {
      (server ? IoServer.Bind("localhost", port)).await must beAnInstanceOf[IoServer.Bound]
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
    worker.stop()
  }

  class TestServer(ioWorker: IoWorker) extends IoServer(ioWorker) {
    override def receive = super.receive orElse {
      case IoWorker.Received(handle, buffer) => ioWorker ! IoWorker.Send(handle, buffer)
    }
  }

  class TestClient(ioWorker: IoWorker) extends IoClient(ioWorker) {
    var requests = Map.empty[Handle, ActorRef]
    override def receive = super.receive orElse {
      case (x: String, handle: Handle) =>
        requests += handle -> sender
        ioWorker ! IoWorker.Send(handle, ByteBuffer.wrap(x.getBytes))
      case IoWorker.Received(handle, buffer) =>
        requests(handle) ! new String(buffer.array, 0, buffer.limit)
    }
  }

  def request(payload: String) = {
    for {
      IoClient.Connected(handle) <- (client ? IoClient.Connect("localhost", port)).mapTo[IoClient.Connected]
      response <- (client ? (payload -> handle)).mapTo[String]
    } yield {
      worker ! IoWorker.Close(handle, CleanClose)
      response
    }
  }

}
