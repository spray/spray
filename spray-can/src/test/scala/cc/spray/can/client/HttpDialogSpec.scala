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

package cc.spray.can.client

import cc.spray.can.model.{HttpResponse, HttpRequest}
import cc.spray.can.server.HttpServer
import cc.spray.io.IoWorker
import cc.spray.io.pipelines.MessageHandlerDispatch.SingletonHandler
import cc.spray.util._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Duration
import org.specs2.mutable.Specification

class HttpDialogSpec extends Specification {
  implicit val system = ActorSystem()
  val ioWorker = new IoWorker(system).start()
  val port = 8899

  step {
    val handler = system.actorOf(Props(behavior = ctx => {
      case x: HttpRequest => ctx.sender ! HttpResponse().withBody(x.uri)
    }))
    val server = system.actorOf(Props(new HttpServer(ioWorker, SingletonHandler(handler))))
    server.ask(HttpServer.Bind("localhost", port))(Duration("1 s")).await
  }

  val client = system.actorOf(Props(new HttpClient(ioWorker)))

  "An HttpDialog" should {
    "be able to complete a simple request/response dialog" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .end
        .map(_.bodyAsString)
        .await === "/foo"
    }
    "be able to complete a pipelined 3 requests dialog" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .send(HttpRequest(uri = "/bar"))
        .send(HttpRequest(uri = "/baz"))
        .end
        .map(_.map(_.bodyAsString))
        .await === "/foo" :: "/bar" :: "/baz" :: Nil
    }
    "be able to complete an unpipelined 3 requests dialog" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .awaitResponse
        .send(HttpRequest(uri = "/bar"))
        .awaitResponse
        .send(HttpRequest(uri = "/baz"))
        .end
        .map(_.map(_.bodyAsString))
        .await === "/foo" :: "/bar" :: "/baz" :: Nil
    }
    "be able to complete a dialog with 3 replies" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .reply(response => HttpRequest(uri = response.bodyAsString + "/a"))
        .reply(response => HttpRequest(uri = response.bodyAsString + "/b"))
        .reply(response => HttpRequest(uri = response.bodyAsString + "/c"))
        .end
        .map(_.bodyAsString)
        .await === "/foo/a/b/c"
    }
  }

  step {
    system.shutdown()
    ioWorker.stop()
  }

}
