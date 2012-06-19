/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.client

import org.specs2.Specification
import org.specs2.specification.Step
import com.typesafe.config.ConfigFactory
import akka.util.Duration
import akka.dispatch.Future
import akka.pattern.ask
import cc.spray.can.client.HttpClient
import cc.spray.can.server.HttpServer
import cc.spray.can.model.{HttpRequest => CHttpRequest, HttpResponse => CHttpResponse, HttpHeader => CHttpHeader}
import cc.spray.http.{HttpMethods, HttpResponse, HttpRequest}
import cc.spray.io.IoWorker
import cc.spray.io.pipelines.MessageHandlerDispatch._
import cc.spray.util._
import DispatchStrategies._
import akka.actor.{Actor, Props, ActorSystem}

class HttpConduitSpec extends Specification {
  implicit val system = ActorSystem()

  def is =                                                                    sequential^
  "An HttpConduit with max. 4 connections and NonPipelined strategy should"   ^
    "properly deliver the result of a simple request"                         ! oneRequest(NonPipelined())^
    "properly deliver the results of 100 requests"                            ! hundredRequests(NonPipelined())^
                                                                              p^
  "An HttpConduit with max. 4 connections and Pipelined strategy should"      ^
    "properly deliver the result of a simple request"                         ! oneRequest(Pipelined)^
    "properly deliver the results of 100 requests"                            ! hundredRequests(Pipelined)^
                                                                              p^
  "An HttpConduit should"                                                     ^
    "retry requests whose sending has failed"                                 ! retryFailedSend^
    "honor the pipelined strategy when retrying"                              ! retryPipelined^
                                                                              Step(system.shutdown())

  val port = 17242
  val client = {
    val ioWorker = new IoWorker(system).start()
    system.registerOnTermination(ioWorker.stop())
    def response = CHttpResponse(headers = List(CHttpHeader("Content-Type", "text/plain; charset=ISO-8859-1")))
    val handler = system.actorOf(Props(
      new Actor {
        var counter = 0
        def receive = {
          case x: CHttpRequest if x.uri.startsWith("/reply1of3") && ({counter += 1; counter} % 3) != 0 => // drop
          case CHttpRequest(method, uri, _, body, _) => sender ! response.withBody {
            method + "|" + uri + (if (body.length == 0) "" else "|" + new String(body, "ASCII"))
          }
          case HttpServer.RequestTimeout(request) => sender ! response.withBody("TIMEOUT")
        }
      }
    ))
    system.actorOf(Props(new HttpServer(ioWorker, SingletonHandler(handler))))
      .ask(HttpServer.Bind("localhost", port))(Duration("1 s"))
      .await // block until the server is actually bound
    system.actorOf(Props(new HttpClient(ioWorker)))
  }

  def newConduit(strategy: DispatchStrategy, maxConnections: Int = 4) =
    new HttpConduit(client, "127.0.0.1", port,
      config = ConfigFactory.parseString("spray.client.max-connections = " + maxConnections),
      dispatchStrategy = strategy
    )

  def oneRequest(strategy: DispatchStrategy) = {
    newConduit(strategy).sendReceive(HttpRequest())
      .await
      .copy(headers = Nil) === HttpResponse(200, "GET|/")
  }

  def hundredRequests(strategy: DispatchStrategy) = {
    val conduit = newConduit(strategy)
    val requests = Seq.tabulate(10)(index => HttpRequest(uri = "/" + index))
    val responseFutures = requests.map(conduit.sendReceive(_))
    responseFutures.zipWithIndex.map { case (future, index) =>
      future.await.copy(headers = Nil) === HttpResponse(200, "GET|/" + index)
    }.reduceLeft(_ and _)
  }

  def retryFailedSend = {
    val conduit = newConduit(NonPipelined())
    def send = conduit.sendReceive(HttpRequest(uri = "/reply1of3"))
    val fut = send.flatMap(r1 => send.map(r2 => r1 -> r2))
    val (r1, r2) = fut.await
    r1.content === r2.content
  }

  def retryPipelined = {
    val conduit = newConduit(Pipelined, maxConnections = 1)
    val requests = List(
      HttpRequest(uri = "/reply1of3/a"),
      HttpRequest(uri = "/reply1of3/b"),
      HttpRequest(uri = "/reply1of3/c")
    )
    val future = Future.traverse(requests)(conduit.sendReceive).flatMap { responses1 =>
      Future.traverse(requests)(conduit.sendReceive).map(responses2 => responses1.zip(responses2))
    }
    future.await.map { case (a, b) => a.content === b.content }.reduceLeft(_ and _)
  }

  def retryPosts = {
    val conduit = newConduit(Pipelined, maxConnections = 1)
    conduit.sendReceive(HttpRequest(HttpMethods.POST, "/reply1of3/a")).await must
      throwA(new RuntimeException("Connection closed, reason: RequestTimeout"))
  }

}