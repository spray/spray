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

import akka.actor.{Kill, Actor}
import org.slf4j.LoggerFactory

class TestService(id: String) extends Actor {
  val log = LoggerFactory.getLogger(getClass)
  self.id = id

  protected def receive = {

    case HttpRequest("GET", "/", _, _, _, complete) => complete {
      HttpResponse(
        status = 200,
        headers = List(HttpHeader("Server", "spray-can/test")),
        body = "Say hello to a spray-can app".getBytes("ASCII")
      )
    }

    case HttpRequest("POST", "/crash", _, _, _, complete) => {
      complete {
        HttpResponse(
          status = 200,
          body = "Hai! (about to kill the HttpServer)".getBytes("ASCII")
        )
      }
      self ! Kill
    }

    case HttpRequest("POST", "/stop", _, _, _, complete) => {
      complete {
        HttpResponse(
          status = 200,
          body = "Shutting down the spray-can server...".getBytes("ASCII")
        )
      }
      Actor.registry.shutdownAll()
    }

    case HttpRequest("GET", "/stats", _, _, _, complete) => {
      val serverActor = Actor.registry.actorsFor("spray-can-server").head
      (serverActor ? GetServerStats).mapTo[ServerStats].onComplete {
        _.value.get match {
          case Right(stats) => complete {
            HttpResponse(
              status = 200,
              body = (
                "Uptime: " + (stats.uptime / 1000.0) + " sec\n" +
                "Requests dispatched: " + stats.requestsDispatched + '\n'
              ).getBytes("ASCII")
            )
          }
          case Left(ex) => complete {
            HttpResponse(
              status = 500,
              body = ("Couldn't get server stats due to " + ex).getBytes("ASCII")
            )
          }
        }
      }
    }

    case HttpRequest(_, _, _, _, _, complete) => complete {
      HttpResponse(status = 404, body = "Unknown command!".getBytes("ASCII"))
    }
  }
}