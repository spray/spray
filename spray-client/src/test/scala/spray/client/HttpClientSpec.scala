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

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.server.HttpServer
import spray.httpx.RequestBuilding
import spray.io._
import spray.http._
import spray.util._
import HttpHeaders._
import pipelining._


class HttpClientSpec extends Specification with RequestBuilding {
  implicit val timeout: Timeout = 5 seconds span
  implicit val system = ActorSystem()
  val port = 17249

  val httpClient = system.actorOf(Props(new HttpClient()), "http-client")

  spray.http.warmUp()
  installDebuggingEventStreamLoggers()

  step {
    val handler = system.actorOf(Props(
      new Actor with SprayActorLogging {
        def receive = {
          case x@ HttpRequest(method, uri, _, entity, _) =>
            log.debug("Responding with " + x)
            sender ! response(method + "|" + uri + (if (entity.isEmpty) "" else "|" + entity.asString))
          case HttpServer.Closed(_, reason) => log.debug("Received Closed event with reason " + reason)
        }
        def response(s: String) = HttpResponse(entity = s, headers = List(`Content-Type`(ContentType.`text/plain`)))
      }
    ), "handler")
    system.actorOf(Props(new HttpServer(SingletonHandler(handler))), "test-server")
      .ask(HttpServer.Bind("localhost", port, tag = LogMark("SERVER"))).await
  }

  "The HttpClient" should {
    "properly process a request with Host header" in {
      val pipeline = addHeader(Host("localhost", port)) ~> sendReceive(httpClient)
      pipeline(Get("/abc", "Great Content")).await.withHeaders(Nil) === HttpResponse(entity = "GET|/abc|Great Content")
    }
    "properly process a request with the host specified in the URI" in {
      val pipeline = sendReceive(httpClient)
      pipeline(Get(s"http://localhost:$port/abc", "x")).await.withHeaders(Nil) === HttpResponse(entity = "GET|/abc|x")
    }
  }

  step(system.shutdown())

}