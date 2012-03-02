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
import akka.actor.{PoisonPill, ActorLogging, Actor}
import cc.spray.io.util.DateTime

class TestService extends Actor with ActorLogging {
  import HttpMethods._

  protected def receive = {

    case HttpRequest(GET, "/", _, _, _) =>
      sender ! index

    case HttpRequest(GET, "/ping", _, _, _) =>
      sender ! response("PONG!")

    case HttpRequest(GET, "/stream", _, _, _) =>
      val savedSender = sender
      savedSender ! ChunkedResponseStart(HttpResponse(headers = defaultHeaders))
      val chunkGenerator = context.system.scheduler.schedule(100.millis, 100.millis, new Runnable {
        def run() { savedSender ! MessageChunk(DateTime.now.toIsoDateTimeString + ", ") }
      })
      context.system.scheduler.scheduleOnce(20.seconds, new Runnable {
        def run() {
          chunkGenerator.cancel()
          savedSender ! MessageChunk("\nStopped...")
          savedSender ! ChunkedMessageEnd()
        }
      })

//    case HttpRequest(GET, "/crash", _, _, _) =>
//      sender ! response("Hai! (about to kill the HttpServer, watch the log for the automatic restart)")
//      serverActor ! Kill

    case HttpRequest(GET, "/timeout", _, _, _) =>
      // we simply drop the request triggering a timeout

    case HttpRequest(GET, "/stop", _, _, _) =>
      sender ! response("Shutting down in 1 second ...")
      context.system.scheduler.scheduleOnce(1.second, new Runnable { def run() { serverActor ! PoisonPill } })

    case _: HttpRequest => sender ! response("Unknown resource!", 404)

//    case Timeout(method, uri, _, _, _, complete) => complete {
//      HttpResponse(status = 500).withBody("The " + method + " request to '" + uri + "' has timed out...")
//    }
  }

  ////////////// helpers //////////////

  val defaultHeaders = List(HttpHeader("Content-Type", "text/plain"))

  lazy val serverActor = context.actorFor("/user/http-server")

  def response(msg: String, status: Int = 200) = HttpResponse(status, defaultHeaders , msg.getBytes("ISO-8859-1"))

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
}