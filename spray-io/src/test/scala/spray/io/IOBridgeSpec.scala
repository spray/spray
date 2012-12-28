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
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import org.specs2.mutable.Specification
import org.specs2.matcher.Matcher
import spray.util._
import ConnectionCloseReasons._
import java.net.InetSocketAddress
import spray.io.IOBridge.Key


class IOBridgeSpec extends Specification {
  implicit val timeout: Timeout = Duration(1, "sec")
  implicit val system = ActorSystem("IOBridgeSpec")
  val port = 23456

  installDebuggingEventStreamLoggers()
  sequential

  "An IOBridge" should {

    "properly bind a test server" in {
      val server = system.actorOf(Props(new TestServer), name = "test-server")
      val bindTag = LogMark("SERVER")
      server.ask(IOServer.Bind("localhost", port, tag = bindTag)).mapTo[IOServer.Bound].map(_.tag).await === bindTag
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

  class TestServer extends IOServer {
    override def bound(endpoint: InetSocketAddress, bindingKey: Key, bindingTag: Any): Receive =
      super.bound(endpoint, bindingKey, bindingTag) orElse {
        case IOBridge.Received(handle, buffer) => sender ! IOBridge.Send(handle, buffer)
      }
  }

  def request(payload: String, closeReason: CloseCommandReason = CleanClose) = {
    import IOClientConnectionActor._
    val client = system.actorOf(Props(new IOClientConnectionActor()))
    for {
      Connected(_, _)     <- client ? Connect("localhost", port)
      Received(_, buffer) <- client ? Send(BufferBuilder(payload).toByteBuffer)
      Closed(_, reason)   <- client ? Close(closeReason)
    } yield buffer.drainToString -> reason
  }
}
