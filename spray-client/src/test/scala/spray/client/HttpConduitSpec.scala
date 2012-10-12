/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.client

import org.specs2.mutable.Specification
import com.typesafe.config.ConfigFactory
import util.Random
import akka.util.Duration
import akka.dispatch.Future
import akka.pattern.ask
import akka.actor._
import spray.can.client.HttpClient
import spray.can.server.HttpServer
import spray.httpx.encoding.Gzip
import spray.io._
import spray.http._
import spray.util._
import DispatchStrategies._
import HttpHeaders._


class HttpConduitSpec extends Specification {
  sequential
  implicit val system = ActorSystem()

  //logEventStreamOf[UnhandledMessage]

  val port = 17242
  val client = {
    val ioBridge = new IOBridge(system).start()
    system.registerOnTermination(ioBridge.stop())
    def response(s: String) = HttpResponse(entity = HttpBody(s), headers = List(`Content-Type`(ContentType.`text/plain`)))
    val handler = system.actorOf(Props(
      new Actor with ActorLogging {
        var dropNext = true
        val random = new Random(39)
        def receive = {
          case HttpRequest(_, "/compressedResponse", _, _, _) =>
            sender ! Gzip.encode(HttpResponse(entity = "content"))
          case x: HttpRequest if x.uri.startsWith("/drop1of2") && dropNext =>
            log.debug("Dropping " + x)
            dropNext = random.nextBoolean()
          case x@ HttpRequest(method, uri, _, entity, _) =>
            log.debug("Responding with " + x)
            dropNext = random.nextBoolean()
            sender ! response(method + "|" + uri + (if (entity.isEmpty) "" else "|" + entity.asString))
          case Timeout(request) => sender ! response("TIMEOUT")
        }
      }
    ), "handler")
    system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler))), "http-server")
      .ask(HttpServer.Bind("localhost", port))(Duration("1 s"))
      .await // block until the server is actually bound
    system.actorOf(Props(new HttpClient(ioBridge)), "http-client")
  }

  "An HttpConduit with max. 4 connections and NonPipelined strategy" should {
    "properly deliver the result of a simple request" in {
      oneRequest(NonPipelined())
    }
    "properly deliver the results of 100 requests" in {
      hundredRequests(NonPipelined())
    }
  }
  "An HttpConduit with max. 4 connections and Pipelined strategy" should {
    "properly deliver the result of a simple request" in {
      oneRequest(Pipelined)
    }
    "properly deliver the results of 100 requests" in {
      hundredRequests(Pipelined)
    }
  }

  "An HttpConduit" should {
    "retry GET requests whose sending has failed" in {
      val pipeline = newConduitPipeline(NonPipelined())
      def send = pipeline(HttpRequest(uri = "/drop1of2"))
      val fut = send.flatMap(r1 => send.map(r2 => r1 -> r2))
      val (r1, r2) = fut.await
      r1.entity === r2.entity
    }
    "honor the pipelined strategy when retrying" in {
      val pipeline = newConduitPipeline(Pipelined, maxConnections = 1)
      val requests = List(
        HttpRequest(uri = "/drop1of2/a"),
        HttpRequest(uri = "/drop1of2/b"),
        HttpRequest(uri = "/drop1of2/c")
      )
      val future = Future.traverse(requests)(pipeline).flatMap { responses1 =>
        Future.traverse(requests)(pipeline).map(responses2 => responses1.zip(responses2))
      }
      future.await.map { case (a, b) => a.entity === b.entity }.reduceLeft(_ and _)
    }
    "deliver the incoming responses fully parsed" in {
      import HttpConduit._
      val pipeline = newConduitPipeline(NonPipelined())
      val response = pipeline(Get("/compressedResponse")).await
      response.encoding === HttpEncodings.gzip
    }
  }

  step(system.shutdown())

  def newConduitPipeline(strategy: DispatchStrategy, maxConnections: Int = 4) = {
    val conduit = system.actorOf(Props {
      new HttpConduit(client, "127.0.0.1", port,
        settings = ConfigFactory.parseString("spray.client.max-connections = " + maxConnections),
        dispatchStrategy = strategy
      )
    })
    HttpConduit.sendReceive(conduit)
  }

  def oneRequest(strategy: DispatchStrategy) = {
    val pipeline = newConduitPipeline(strategy)
    pipeline(HttpRequest()).await.copy(headers = Nil) === HttpResponse(200, "GET|/")
  }

  def hundredRequests(strategy: DispatchStrategy) = {
    val pipeline = newConduitPipeline(strategy)
    val requests = Seq.tabulate(10)(index => HttpRequest(uri = "/" + index))
    val responseFutures = requests.map(pipeline)
    responseFutures.zipWithIndex.map { case (future, index) =>
      future.await.copy(headers = Nil) === HttpResponse(200, "GET|/" + index)
    }.reduceLeft(_ and _)
  }

}