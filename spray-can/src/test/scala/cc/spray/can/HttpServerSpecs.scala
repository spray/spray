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

import org.specs2._
import specification.Step
import akka.actor.{Scheduler, Actor}
import java.util.concurrent.TimeUnit
import akka.util.Duration

trait HttpServerSpecs extends Specification {
  this: HttpClientServerSpec =>

  class TestService extends Actor {
    self.id = "server-test-server"
    var delayedResponse: HttpResponse => Unit = _
    protected def receive = {
      case RequestContext(HttpRequest(_, "/wait200", _, _, _), _, complete) => {
        Scheduler.scheduleOnce(() => complete(HttpResponse()), 200, TimeUnit.MILLISECONDS)
      }
      case RequestContext(HttpRequest(_, "/delayResponse", _, _, _), _, complete) =>
        delayedResponse = complete
      case RequestContext(HttpRequest(_, "/getThisAndDelayedResponse", _, _, _), _, complete) =>
        complete(HttpResponse().withBody("secondResponse"))          // first complete the second request
        delayedResponse(HttpResponse().withBody("delayedResponse"))  // then complete the first request
      case RequestContext(HttpRequest(method, uri, _, _, _), _, complete) => complete {
        HttpResponse().withBody(method + "|" + uri)
      }
      case Timeout(RequestContext(_, _, complete)) => complete {
        HttpResponse().withBody("TIMEOUT")
      }
    }
  }

  def serverSpecs =

  "This spec exercises a new HttpServer instance with test requests" ^
                                                                                    Step(start())^
                                                                                    p^
  "simple one-request dialog"                                                       ! oneRequestDialog^
  //"two request dialog with response reordering"                                     ! responseReorderingDialog^
  "time-out request"                                                                ! timeoutRequest^
  "idle-time-out connection"                                                        ! timeoutConnection^
                                                                                    end

  import HttpClient._

  private def oneRequestDialog = {
    dialog()
            .send(HttpRequest(uri = "/yeah"))
            .end
            .get.bodyAsString mustEqual "GET|/yeah"
  }

  private def responseReorderingDialog = {
    dialog()
            .send(HttpRequest(uri = "/delayResponse"))
            .send(HttpRequest(uri = "/getThisAndDelayedResponse"))
            .end
            .get.map(_.bodyAsString).mkString(",") mustEqual "delayedResponse,secondResponse"
  }

  private def timeoutRequest = {
    dialog()
            .send(HttpRequest(uri = "/wait200"))
            .end
            .get.bodyAsString mustEqual "TIMEOUT"
  }

  private def timeoutConnection = {
    dialog()
            .waitIdle(Duration("500 ms"))
            .send(HttpRequest())
            .end
            .await.exception.get.getMessage mustEqual "Cannot send request due to closed connection"
  }

  private def dialog(port: Int = 17242) =
    HttpDialog(host = "localhost", port = port, clientActorId = "server-test-client")

  private def start() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(ServerConfig(
      port = 17242,
      serviceActorId = "server-test-server",
      timeoutActorId = "server-test-server",
      requestTimeout = 100, timeoutCycle = 50,
      idleTimeout = 200, reapingCycle = 100
    ))).start()
    Actor.actorOf(new HttpClient(ClientConfig(clientActorId = "server-test-client", requestTimeout = 1000))).start()
  }
}
