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
import HttpMethods._

class HttpClientServerSpec extends Specification with HttpClientSpecs { def is =

  sequential^
  "This spec exercises one HttpClient and one HttpServer instance"    ^
  "with various request/response patterns"                            ^
                                                                      p^
                                                                      Step(start())^
  "simple one-request dialog"                                         ! oneRequestDialog^
  "one-request dialog with HTTP/1.0 terminated-by-close reponse"      ! terminatedByCloseDialog^
  "request-response dialog"                                           ! requestResponse^
  "non-pipelined request-request dialog"                              ! nonPipelinedRequestRequest^
  "pipelined request-request dialog"                                  ! pipelinedRequestRequest^
  "pipelined request-request dialog with response reordering"         ! responseReorderingDialog^
  "multi-request pipelined dialog with response reordering"           ! multiRequestDialog^
  "pipelined request-request dialog with HEAD requests"               ! pipelinedRequestsWithHead^
  "one-request dialog with a chunked response"                        ! oneRequestChunkedResponse^
  "one-request dialog with a chunked request"                         ! oneRequestChunkedRequest^
  "pipelined requests dialog with one chunked request"                ! pipelinedRequestsWithChunkedRequest^
  "pipelined requests dialog with one chunked response"               ! pipelinedRequestsWithChunkedResponse^
  "time-out request"                                                  ! timeoutRequest^
  "idle-time-out connection"                                          ! timeoutConnection^
                                                                      p^
  clientSpecs                                                         ^
                                                                      Step(Actor.registry.shutdownAll())

  private class TestService extends Actor {
    self.id = "server-test-server"
    var delayedResponse: RequestResponder = _
    protected def receive = {
      case RequestContext(HttpRequest(_, "/delayResponse", _, _, _), _, responder) =>
        delayedResponse = responder
      case RequestContext(HttpRequest(_, "/getThisAndDelayedResponse", _, _, _), _, responder) =>
        responder.complete(HttpResponse().withBody("secondResponse"))          // first complete the second request
        delayedResponse.complete(HttpResponse().withBody("delayedResponse"))  // then complete the first request
      case RequestContext(HttpRequest(_, path, _, _, _), _, responder) if path.startsWith("/multi/") =>
        val delay = (scala.math.random * 80.0).toLong
        Scheduler.scheduleOnce(() => responder.complete(HttpResponse().withBody(path.last.toString)), delay, TimeUnit.MILLISECONDS)
      case RequestContext(HttpRequest(_, "/chunked", _, _, _), _, responder) => {
        val chunker = responder.startChunkedResponse(HttpResponse(201, List(HttpHeader("Fancy", "cool"))))
        chunker.sendChunk(MessageChunk("1"))
        chunker.sendChunk(MessageChunk("2345"))
        chunker.sendChunk(MessageChunk("6789ABCD"))
        chunker.sendChunk(MessageChunk("EFGHIJKLMNOPQRSTUVWXYZ"))
        chunker.close()
      }
      case RequestContext(HttpRequest(_, "/wait200", _, _, _), _, responder) =>
        Scheduler.scheduleOnce(() => responder.complete(HttpResponse()), 200, TimeUnit.MILLISECONDS)
      case RequestContext(HttpRequest(_, "/terminatedByClose", _, _, _), _, responder) => responder.complete {
        HttpResponse(protocol = HttpProtocols.`HTTP/1.0`).withBody("This body is terminated by closing the connection!")
      }
      case RequestContext(HttpRequest(method, uri, _, body, _), _, responder) => responder.complete {
        HttpResponse().withBody(method + "|" + uri + (if (body.length == 0) "" else "|" + new String(body, "ASCII")))
      }
      case Timeout(_, _, _, _, _, complete) =>
        complete(HttpResponse().withBody("TIMEOUT"))
    }
  }

  import HttpClient._

  private def oneRequestDialog = {
    dialog
            .send(HttpRequest(uri = "/yeah"))
            .end
            .get.bodyAsString mustEqual "GET|/yeah"
  }

  private def terminatedByCloseDialog = {
    dialog
            .send(HttpRequest(uri = "/terminatedByClose"))
            .end
            .get.bodyAsString mustEqual "This body is terminated by closing the connection!"
  }

  private def requestResponse = {
    def respond(res: HttpResponse) = HttpRequest(POST).withBody("(" + res.bodyAsString + ")")

    dialog
            .send(HttpRequest(GET, "/abc"))
            .reply(respond)
            .end
            .get.bodyAsString mustEqual "POST|/|(GET|/abc)"
  }

  private def nonPipelinedRequestRequest = {
      dialog
              .send(HttpRequest(DELETE, "/abc"))
              .awaitResponse
              .send(HttpRequest(PUT, "/xyz"))
              .end
              .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc, PUT|/xyz"
    }

  private def pipelinedRequestRequest = {
    dialog
            .send(HttpRequest(DELETE, "/abc"))
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/abc, PUT|/xyz"
  }

  private def responseReorderingDialog = {
    dialog
            .send(HttpRequest(uri = "/delayResponse"))
            .send(HttpRequest(uri = "/getThisAndDelayedResponse"))
            .end
            .get.map(_.bodyAsString).mkString(",") mustEqual "delayedResponse,secondResponse"
  }

  private def multiRequestDialog = {
    dialog
            .send(HttpRequest(uri = "/multi/1"))
            .send(HttpRequest(uri = "/multi/2"))
            .send(HttpRequest(uri = "/multi/3"))
            .send(HttpRequest(uri = "/multi/4"))
            .send(HttpRequest(uri = "/multi/5"))
            .send(HttpRequest(uri = "/multi/6"))
            .send(HttpRequest(uri = "/multi/7"))
            .send(HttpRequest(uri = "/multi/8"))
            .send(HttpRequest(uri = "/multi/9"))
            .end
            .get.map(_.bodyAsString).mkString(",") mustEqual "1,2,3,4,5,6,7,8,9"
  }

  private def pipelinedRequestsWithHead = {
    dialog
            .send(HttpRequest(DELETE, "/abc"))
            .send(HttpRequest(HEAD, "/def"))
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map { r =>
              (r.headers.collect({ case HttpHeader("Content-Length", cl) => cl }).head.toInt, r.bodyAsString)
            } mustEqual Seq((11, "DELETE|/abc"), (9, ""), (8, "PUT|/xyz"))
  }

  private def oneRequestChunkedResponse = {
    dialog
            .send(HttpRequest(GET, "/chunked"))
            .end
            .get.bodyAsString mustEqual "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  }

  private def oneRequestChunkedRequest = {
    dialog
            .sendChunked(HttpRequest(GET)) { chunker =>
              chunker.sendChunk(MessageChunk("1"))
              chunker.sendChunk(MessageChunk("2"))
              chunker.sendChunk(MessageChunk("3"))
              chunker.close()
            }
            .end
            .get.bodyAsString mustEqual "GET|/|123"
  }

  private def pipelinedRequestsWithChunkedRequest = {
    dialog
            .send(HttpRequest(DELETE, "/delete"))
            .sendChunked(HttpRequest(PUT, "/put")) { chunker =>
              chunker.sendChunk(MessageChunk("1"))
              chunker.sendChunk(MessageChunk("2"))
              chunker.sendChunk(MessageChunk("3"))
              chunker.close()
            }
            .send(HttpRequest(GET, "/get"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "DELETE|/delete, PUT|/put|123, GET|/get"
  }

  private def pipelinedRequestsWithChunkedResponse = {
    dialog
            .send(HttpRequest(POST, "/"))
            .send(HttpRequest(GET, "/chunked"))
            .send(HttpRequest(PUT, "/xyz"))
            .end
            .get.map(_.bodyAsString).mkString(", ") mustEqual "POST|/, 123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ, PUT|/xyz"
  }

  private def timeoutRequest = {
    dialog
            .send(HttpRequest(uri = "/wait200"))
            .end
            .get.bodyAsString mustEqual "TIMEOUT"
  }

  private def timeoutConnection = {
    dialog
            .waitIdle(Duration("500 ms"))
            .send(HttpRequest())
            .end
            .await.exception.get.getMessage mustEqual "Cannot send request due to closed connection"
  }

  private def dialog = HttpDialog(host = "localhost", port = 17242, clientActorId = "server-test-client")

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

