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

import com.typesafe.config.ConfigFactory
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.duration._
import org.specs2.mutable.Specification
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.server.HttpServer
import spray.httpx.encoding.Gzip
import spray.io._
import spray.http._
import spray.util._
import HttpHeaders._
import pipelining._


class HttpHostConnectorSpec extends Specification {
  implicit val timeout: Timeout = 5 seconds span
  implicit val system = ActorSystem()
  val port = 17242

  installDebuggingEventStreamLoggers()
  sequential
  //only("deliver the incoming responses fully parsed")

  step {
    val handler = system.actorOf(Props(
      new Actor with SprayActorLogging {
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
          case Timedout(request) => sender ! response("TIMEOUT")
          case HttpServer.Closed(_, reason) => log.debug("Received Closed event with reason " + reason)
        }
        def response(s: String) = HttpResponse(entity = s, headers = List(`Content-Type`(ContentType.`text/plain`)))
      }
    ), "handler")
    system.actorOf(Props(new HttpServer(SingletonHandler(handler))), "test-server")
      .ask(HttpServer.Bind("localhost", port, tag = LogMark("SERVER"))).await
  }

  "An HttpConduit with max. 4 connections and pipelining enabled" should {
    "properly deliver the result of a simple request" in {
      oneRequest(pipelined = false)
    }
    "properly deliver the results of 100 requests" in {
      hundredRequests(pipelined = false)
    }
  }
  "An HttpHostConnector with max. 4 connections and pipelining disabled" should {
    "properly deliver the result of a simple request" in {
      oneRequest(pipelined = true)
    }
    "properly deliver the results of 100 requests" in {
      hundredRequests(pipelined = true)
    }
  }

  "An HttpHostConnector" should {
    "retry GET requests whose sending has failed" in {
      val pipeline = newHostConnector(pipelined = false)
      def send = pipeline(HttpRequest(uri = "/drop1of2"))
      val fut = send.flatMap(r1 => send.map(r2 => r1 -> r2))
      val (r1, r2) = fut.await
      r1.entity === r2.entity
    }
    "honor the pipelined strategy when retrying" in {
      val pipeline = newHostConnector(pipelined = true, maxConnections = 1)
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
      val pipeline = newHostConnector(pipelined = false)
      val response = pipeline(Get("/compressedResponse")).await
      response.encoding === HttpEncodings.gzip
    }
  }

  step(system.shutdown())

  def newHostConnector(pipelined: Boolean, maxConnections: Int = 4) = {
    val connector = system.actorOf(Props {
      new HttpHostConnector("localhost", port, ConfigFactory.parseString {
        "spray.client.max-connections = " + maxConnections + "\n" +
        "spray.client.pipeline-requests = " + pipelined
      }, defaultConnectionTag = LogMark("CONNECTOR"))
    })
    sendReceive(connector)
  }

  def oneRequest(pipelined: Boolean) = {
    val pipeline = newHostConnector(pipelined)
    pipeline(HttpRequest()).await.copy(headers = Nil) === HttpResponse(200, "GET|/")
  }

  def hundredRequests(pipelined: Boolean) = {
    val pipeline = newHostConnector(pipelined)
    val requests = Seq.tabulate(10)(index => HttpRequest(uri = "/" + index))
    val responseFutures = requests.map(pipeline)
    responseFutures.zipWithIndex.map { case (future, index) =>
      future.await.copy(headers = Nil) === HttpResponse(200, "GET|/" + index)
    }.reduceLeft(_ and _)
  }

}