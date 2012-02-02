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

package cc.spray
package client

import org.specs2.Specification
import org.specs2.specification.Step
import akka.actor.Actor
import can.{HttpRequest => _, HttpResponse => _, _}
import http._
import DispatchStrategies._

class HttpConduitSpec extends Specification { def is =
                                                                              sequential^
                                                                              Step(start())^
  "An HttpConduit with max. 4 connections and NonPipelined strategy should"  ^
  "properly deliver the result of a simple request"                           ! oneRequest(new NonPipelined)^
  "properly deliver the results of 100 requests"                              ! hundredRequests(new NonPipelined)^
                                                                              p^
  "An HttpConduit with max. 4 connections and Pipelined strategy should"     ^
  "properly deliver the result of a simple request"                           ! oneRequest(Pipelined)^
  "properly deliver the results of 100 requests"                              ! hundredRequests(Pipelined)^
                                                                              Step(Actor.registry.shutdownAll())


  class TestService extends Actor {
    self.id = "clienttest-server"
    protected def receive = {
      case RequestContext(can.HttpRequest(method, uri, _, body, _), _, responder) => responder.complete {
        response.withBody(method + "|" + uri + (if (body.length == 0) "" else "|" + new String(body, "ASCII")))
      }
      case Timeout(_, _, _, _, _, complete) => complete(response.withBody("TIMEOUT"))
    }
    def response = can.HttpResponse(headers = List(can.HttpHeader("Content-Type", "text/plain; charset=ISO-8859-1")))
  }

  def oneRequest(strategy: DispatchStrategy) = {
    val response = newConduit(strategy).sendReceive(HttpRequest()).get
    response.copy(headers = Nil) mustEqual HttpResponse(200, "GET|/")
  }

  def hundredRequests(strategy: DispatchStrategy) = {
    val conduit = newConduit(strategy)
    val requests = Seq.tabulate(10)(index => HttpRequest(uri = "/" + index))
    val responseFutures = requests.map(conduit.sendReceive(_))
    responseFutures.zipWithIndex.map { case (future, index) =>
      future.get.copy(headers = Nil) mustEqual HttpResponse(200, "GET|/" + index)
    }.reduceLeft(_ and _)
  }

  def newConduit(strategy: DispatchStrategy) = new HttpConduit(
    "127.0.0.1", 17242, ConduitConfig(clientActorId = "clienttest-client", dispatchStrategy = strategy)
  )

  def start() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(ServerConfig(
      port = 17242,
      serviceActorId = "clienttest-server",
      timeoutActorId = "clienttest-server"
    ))).start()
    Actor.actorOf(new HttpClient(ClientConfig(clientActorId = "clienttest-client", requestTimeout = 1000))).start()
  }

}