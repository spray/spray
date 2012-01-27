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

import org.slf4j.LoggerFactory
import model._
import java.util.concurrent.TimeUnit
import akka.actor.{PoisonPill, Scheduler, Kill, Actor}
import util.DateTime
import nio.GetStats

/*class TestService(id: String) extends Actor {
  import HttpMethods._

  val log = LoggerFactory.getLogger(getClass)
  self.id = id

  protected def receive = {

    case RequestContext(HttpRequest(GET, "/", _, _, _), _, responder) =>
      responder.complete(index)

    case RequestContext(HttpRequest(GET, "/ping", _, _, _), _, responder) =>
      responder.complete(response("PONG!"))

    case RequestContext(HttpRequest(GET, "/stream", _, _, _), _, responder) =>
      val streamHandler = responder.startChunkedResponse(HttpResponse(headers = defaultHeaders))
      val chunkGenerator = Scheduler.schedule(
        () => streamHandler.sendChunk(MessageChunk(DateTime.now.toIsoDateTimeString + ", ")),
        100, 100, TimeUnit.MILLISECONDS
      )
      Scheduler.scheduleOnce(() => {
        chunkGenerator.cancel(false)
        streamHandler.sendChunk(MessageChunk("\nStopped..."))
        streamHandler.close()
      }, 20500, TimeUnit.MILLISECONDS)

    case RequestContext(HttpRequest(GET, "/stats", _, _, _), _, responder) => {
      (serverActor ? GetStats).mapTo[ServerStats].onComplete { future =>
        future.value.get match {
          case Right(stats) => responder.complete {
            response {
              "Uptime              : " + (stats.uptime / 1000.0) + " sec\n" +
              "Requests dispatched : " + stats.requestsDispatched + '\n' +
              "Requests timed out  : " + stats.requestsTimedOut + '\n' +
              "Requests open       : " + stats.requestsOpen + '\n' +
              "Open connections    : " + stats.connectionsOpen + '\n'
            }
          }
          case Left(ex) => responder.complete(response("Couldn't get server stats due to " + ex, status = 500))
        }
      }
    }

    case RequestContext(HttpRequest(GET, "/crash", _, _, _), _, responder) =>
      responder.complete(response("Hai! (about to kill the HttpServer, watch the log for the automatic restart)"))
      serverActor ! Kill

    case RequestContext(HttpRequest(GET, "/timeout", _, _, _), _, _) =>
      // we simply drop the request triggering a timeout

    case RequestContext(HttpRequest(GET, "/stop", _, _, _), _, responder) =>
      responder.complete(response("Shutting down in 1 second ..."))
      Scheduler.scheduleOnce(() => Actor.registry.actors.foreach(_ ! PoisonPill), 1000, TimeUnit.MILLISECONDS)

    case RequestContext(HttpRequest(_, _, _, _, _), _, responder) =>
      responder.complete(response("Unknown resource!", 404))

    /*case Timeout(method, uri, _, _, _, complete) => complete {
      HttpResponse(status = 500).withBody("The " + method + " request to '" + uri + "' has timed out...")
    }*/
  }

  ////////////// helpers //////////////

  val defaultHeaders = List(HttpHeader("Content-Type", "text/plain"))

  lazy val serverActor = Actor.registry.actorsFor("spray-can-server").head

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
}*/