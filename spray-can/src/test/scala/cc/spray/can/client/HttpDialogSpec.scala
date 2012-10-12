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

package spray.can.client

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Duration
import org.specs2.mutable.Specification
import spray.can.server.HttpServer
import spray.io._
import spray.util._
import spray.http._


class HttpDialogSpec extends Specification {
  implicit val system = ActorSystem()
  val ioBridge = new IOBridge(system).start()
  val port = 8899

  step {
    val handler = system.actorOf(Props(behavior = ctx => {
      case x: HttpRequest => ctx.sender ! HttpResponse(entity = x.uri)
    }))
    val server = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler))))
    server.ask(HttpServer.Bind("localhost", port))(Duration("1 s")).await
  }

  val client = system.actorOf(Props(new HttpClient(ioBridge)))

  "An HttpDialog" should {
    "be able to complete a simple request/response dialog" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .end
        .map(_.entity.asString)
        .await === "/foo"
    }
    "be able to complete a pipelined 3 requests dialog" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .send(HttpRequest(uri = "/bar"))
        .send(HttpRequest(uri = "/baz"))
        .end
        .map(_.map(_.entity.asString))
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
        .map(_.map(_.entity.asString))
        .await === "/foo" :: "/bar" :: "/baz" :: Nil
    }
    "be able to complete a dialog with 3 replies" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/foo"))
        .reply(response => HttpRequest(uri = response.entity.asString + "/a"))
        .reply(response => HttpRequest(uri = response.entity.asString + "/b"))
        .reply(response => HttpRequest(uri = response.entity.asString + "/c"))
        .end
        .map(_.entity.asString)
        .await === "/foo/a/b/c"
    }
    "properly deliver error messages from the server" in {
      HttpDialog(client, "localhost", port)
        .send(HttpRequest(uri = "/abc/" + ("x" * 2048)))
        .end
        .await.withHeaders(Nil) ===
        HttpResponse(StatusCodes.RequestUriTooLong, "URI length exceeds the configured limit of 2048 characters")
    }
  }

  step {
    system.shutdown()
    ioBridge.stop()
  }

}
