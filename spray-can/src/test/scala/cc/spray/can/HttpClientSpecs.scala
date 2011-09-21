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

trait HttpClientSpecs extends Specification {

  class TestService extends Actor {
    self.id = "client-test-server"
    protected def receive = {
      case RequestContext(HttpRequest(_, "/wait500", _, _, _), _, responder) => {
        Scheduler.scheduleOnce(() => responder.complete(HttpResponse()), 500, TimeUnit.MILLISECONDS)
      }
      case RequestContext(HttpRequest(method, uri, _, _, _), _, responder) => responder.complete {
        HttpResponse().withBody(method + "|" + uri)
      }
    }
  }

  def clientSpecs =

  "This spec exercises a new HttpClient instance against a simple echo HttpServer"  ^
  "by testing several request/response patterns"                                    ^
                                                                                    Step(start())^
                                                                                    p^
  "simple one-request dialog"                                                       ! oneRequestDialog^
  "request-response dialog"                                                         ! requestResponseDialog^
  "non-pipelined request-request dialog"                                            ! nonPipelinedRequestRequestDialog^
  "pipelined request-request dialog"                                                ! pipelinedRequestRequestDialog^
  "pipelined request-request dialog with HEAD requests"                             ! pipelinedRequestRequestWithHeadsDialog^
  "connect to a non-existing server"                                                ! illegalConnect^
  "time-out request"                                                                ! timeoutRequest^
  "idle-time-out connection"                                                        ! timeoutConnection^
                                                                                    end

  import HttpClient._

  private def oneRequestDialog = {
    newDialog()
            .send(HttpRequest(GET, "/yeah"))
            .end
            .get.bodyAsString mustEqual "GET|/yeah"
  }

  private def requestResponseDialog = {
    def respond(res: HttpResponse) = HttpRequest(POST, uri = "(" + res.bodyAsString + ")")

    newDialog()
            .send(HttpRequest(GET, "/abc"))
            .reply(respond)
            .end
            .get.bodyAsString mustEqual "POST|(GET|/abc)"
  }

  private def nonPipelinedRequestRequestDialog = {
    newDialog()
            .send(HttpRequest(DELETE, "/abc"))
            .awaitResponse
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc, PUT|/xyz"
  }

  private def pipelinedRequestRequestDialog = {
    newDialog()
            .send(HttpRequest(DELETE, "/abc"))
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc, PUT|/xyz"
  }

  private def pipelinedRequestRequestWithHeadsDialog = {
    newDialog()
            .send(HttpRequest(DELETE, "/abc"))
            .send(HttpRequest(HEAD, "/def"))
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map { r =>
              (r.headers.collect({ case HttpHeader("Content-Length", cl) => cl }).head.toInt, r.bodyAsString)
            } mustEqual Seq((11, "DELETE|/abc"), (9, ""), (8, "PUT|/xyz"))
  }

  private def illegalConnect = {
    newDialog(16243)
            .send(HttpRequest(GET, "/"))
            .end
            .await.exception.get.toString mustEqual "cc.spray.can.HttpClientException: " +
            "Could not connect to localhost:16243 due to java.net.ConnectException: Connection refused"
  }

  private def timeoutRequest = {
    newDialog()
            .send(HttpRequest(GET, "/wait500"))
            .end
            .await.exception.get.toString mustEqual "cc.spray.can.HttpClientException: Request timed out"
  }

  private def timeoutConnection = {
    newDialog()
            .waitIdle(Duration("500 ms"))
            .send(HttpRequest(GET, "/"))
            .end
            .await.exception.get.toString mustEqual "cc.spray.can.HttpClientException: " +
            "Cannot send request due to closed connection"
  }

  private def newDialog(port: Int = 16242) =
    HttpDialog(host = "localhost", port = port, clientActorId = "client-test-client")

  private def start() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(ServerConfig(port = 16242, serviceActorId = "client-test-server", requestTimeout = 0))).start()
    Actor.actorOf(new HttpClient(ClientConfig(clientActorId = "client-test-client",
      requestTimeout = 100, timeoutCycle = 50, idleTimeout = 200, reapingCycle = 100))).start()
  }
}
