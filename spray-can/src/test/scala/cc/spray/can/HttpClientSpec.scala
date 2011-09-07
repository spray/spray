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
import HttpMethods._
import akka.actor.{Scheduler, Actor}
import java.util.concurrent.TimeUnit
import akka.util.Duration

class HttpClientSpec extends Specification {

  class TestService extends Actor {
    self.id = "client-test-server"
    var requestIndex: Int = 0
    protected def receive = {
      case RequestContext(HttpRequest(_, "/wait500", _, _), _, _, complete) => {
        Scheduler.scheduleOnce(() => complete(HttpResponse()), 500, TimeUnit.MILLISECONDS)
      }
      case RequestContext(HttpRequest(method, uri, _, _), _, _, complete) => complete {
        requestIndex += 1
        HttpResponse().withBody(method + "|" + uri + "|" + requestIndex)
      }
    }
  }

  def is =

  "This spec exercises a new HttpClient instance against a simple echo HttpServer" ^
                                                                                    Step(start())^
                                                                                    p^
  sequential                                                                        ^
  "simple one-request dialog"                                                       ! oneRequestDialog^
  "request-response dialog"                                                         ! requestResponseDialog^
  "non-pipelined request-request dialog"                                            ! requestRequestNonPipelinedDialog^
  "pipelined request-request dialog"                                                ! requestRequestPipelinedDialog^
  "connect to a non-existing server"                                                ! illegalConnect^
  "time-out request"                                                                ! timeoutRequest^
  "idle-time-out connection"                                                        ! timeoutConnection^
                                                                                    Step(stop())

  import HttpClient._

  def oneRequestDialog = {
    newDialog()
            .send(HttpRequest(uri = "/yeah"))
            .end
            .get.bodyAsString mustEqual "GET|/yeah|1"
  }

  def requestResponseDialog = {
    def respond(res: HttpResponse) = HttpRequest(POST, uri = "(" + res.bodyAsString + ")")

    newDialog()
            .send(HttpRequest(uri = "/abc"))
            .reply(respond)
            .end
            .get.bodyAsString mustEqual "POST|(GET|/abc|2)|3"
  }

  def requestRequestNonPipelinedDialog = {
    newDialog()
            .send(HttpRequest(DELETE, uri = "/abc"))
            .awaitResponse
            .send(HttpRequest(PUT, uri = "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc|4, PUT|/xyz|5"
  }

  def requestRequestPipelinedDialog = {
    newDialog()
            .send(HttpRequest(DELETE, uri = "/abc"))
            .send(HttpRequest(PUT, uri = "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc|6, PUT|/xyz|7"
  }

  def illegalConnect = {
    newDialog(16243)
            .send(HttpRequest())
            .end
            .await.exception.get.getMessage mustEqual "java.net.ConnectException: Connection refused"
  }

  def timeoutRequest = {
    newDialog()
            .send(HttpRequest(uri = "/wait500"))
            .end
            .await.exception.get.getMessage mustEqual "Timeout"
  }

  def timeoutConnection = {
    newDialog()
            .waitIdle(Duration("500 ms"))
            .send(HttpRequest())
            .end
            .await.exception.get.getMessage mustEqual "Connection closed"
  }

  def newDialog(port: Int = 16242) = HttpDialog(host = "localhost", port = port, clientActorId = "client-test-client")

  def start() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(SimpleServerConfig(port = 16242, serviceActorId = "client-test-server", requestTimeout = 0))).start()
    Actor.actorOf(new HttpClient(SimpleClientConfig(clientActorId = "client-test-client",
      requestTimeout = 100, timeoutCycle = 50, idleTimeout = 200, reapingCycle = 100))).start()
  }

  def stop() {
    Actor.registry.shutdownAll()
  }
}
