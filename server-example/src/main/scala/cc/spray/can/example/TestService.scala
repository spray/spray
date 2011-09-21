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
import java.nio.charset.Charset
import HttpMethods._
import akka.actor.{Kill, Actor}

class TestService(id: String) extends Actor {
  val log = LoggerFactory.getLogger(getClass)
  self.id = id

  val iso88591 = Charset.forName("ISO-8859-1")
  val headers = List(HttpHeader("Content-Type", "text/plain"))
  lazy val serverActor = Actor.registry.actorsFor("spray-can-server").head

  def response(msg: String, status: Int = 200) = HttpResponse(status, headers, msg.getBytes(iso88591))

  protected def receive = {

    case RequestContext(HttpRequest(GET, "/", _, _, _), _, responder) =>
      responder.complete(response("Say hello to a spray-can app"))

    case RequestContext(HttpRequest(POST, "/crash", _, _, _), _, responder) => {
      responder.complete(response("Hai! (about to kill the HttpServer)"))
      serverActor ! Kill
    }

    case RequestContext(HttpRequest(POST, "/stop", _, _, _), _, responder) => {
      responder.complete(response("Shutting down the spray-can server..."))
      Actor.registry.shutdownAll()
    }

    case RequestContext(HttpRequest(GET, "/stats", _, _, _), _, responder) => {
      (serverActor ? GetStats).mapTo[Stats].onComplete { future =>
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

    case RequestContext(HttpRequest(_, _, _, _, _), _, responder) =>
      responder.complete(response("Unknown command!", 404))
  }
}