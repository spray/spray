/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.can
package example

import model._
import akka.util.duration._
import cc.spray.util.DateTime
import akka.actor._
import cc.spray.io.ConnectionClosedReason
import cc.spray.can.HttpServer.RequestTimeout

class TestService extends Actor with ActorLogging {
  import HttpMethods._

  protected def receive = {

    case HttpRequest(GET, "/", _, _, _) =>
      sender ! index

    case HttpRequest(GET, "/ping", _, _, _) =>
      sender ! response("PONG!")

    case HttpRequest(GET, "/stream", _, _, _) =>
      val peer = sender // since the Props creator is executed asyncly we need to save the sender ref
      context.actorOf(Props(new Streamer(peer, 100)))

//    case HttpRequest(GET, "/crash", _, _, _) =>
//      sender ! response("Hai! (about to kill the HttpServer, watch the log for the automatic restart)")
//      serverActor ! Kill

    case HttpRequest(GET, "/timeout", _, _, _) =>
      // we simply drop the request triggering a timeout

    case HttpRequest(GET, "/stop", _, _, _) =>
      sender ! response("Shutting down in 1 second ...")
      context.system.scheduler.scheduleOnce(1.second, new Runnable { def run() { context.system.shutdown() } })

    case _: HttpRequest => sender ! response("Unknown resource!", 404)

    case x: HttpServer.Closed =>
      context.children.foreach(_ ! CancelStream(sender, x.reason))

    case _: HttpServer.SendCompleted =>
      // we don't care about send confirmations

    case RequestTimeout(request) =>
      sender ! HttpResponse(status = 500).withBody {
        "The " + request.method + " request to '" + request.uri + "' has timed out..."
      }
  }

  ////////////// helpers //////////////

  lazy val serverActor = context.actorFor("/user/http-server")

  def response(msg: String, status: Int = 200) =
    HttpResponse(status, List(HttpHeader("Content-Type", "text/plain")), msg.getBytes("ISO-8859-1"))

  lazy val index = HttpResponse(
    headers = List(HttpHeader("Content-Type", "text/html")),
    body =
      <html>
        <body>
          <h1>Say hello to <i>spray-can</i>!</h1>
          <p>Defined resources:</p>
          <ul>
            <li><a href="/ping">/ping</a></li>
            <li><a href="/stream">/stream</a></li>
            <li><a href="/stats">/stats</a></li>
            <li><a href="/crash">/crash</a></li>
            <li><a href="/timeout">/timeout</a></li>
            <li><a href="/stop">/stop</a></li>
          </ul>
        </body>
      </html>.toString.getBytes("ISO-8859-1")
  )

  case class CancelStream(peer: ActorRef, reason: ConnectionClosedReason)

  class Streamer(peer: ActorRef, var count: Int) extends Actor with ActorLogging {
    log.debug("Starting streaming response ...")
    peer ! ChunkedResponseStart(HttpResponse(headers = defaultHeaders).withBody(" " * 2048))
    val chunkGenerator = context.system.scheduler.schedule(100.millis, 100.millis, self, 'Tick)

    protected def receive = {
      case 'Tick if count > 0 =>
        log.debug("Sending response chunk ...")
        peer ! MessageChunk(DateTime.now.toIsoDateTimeString + ", ")
        count -= 1
      case 'Tick =>
        log.debug("Finalizing response stream ...")
        chunkGenerator.cancel()
        peer ! MessageChunk("\nStopped...")
        peer ! ChunkedMessageEnd()
        context.stop(self)
      case CancelStream(ref, reason) => if (ref == peer) {
        log.debug("Canceling response stream due to {} ...", reason)
        chunkGenerator.cancel()
        context.stop(self)
      }
    }
  }
}