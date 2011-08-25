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

import org.specs2.mutable.Specification
import akka.actor.Actor
import Constants._
import cc.spray.client.{ClientConfig, HttpClient}

class HttpServerSpec extends Specification {

  /*val server = new HttpServer(SimpleConfig(port = 16242, dispatchActorId = "testEndpoint"))
  val client = new HttpClient(ClientConfig())
  class TestService extends Actor {
    self.id = "testEndpoint"
    protected def receive = {
      case r: HttpRequest => r.complete {
        HttpResponse(200, List(HttpHeader("Server", "spray-can/test")), (r.method + "|" + r.uri).getBytes(US_ASCII))
      }
    }
  }

  textFragment("This specification starts a new HttpServer and fires a few test requests against it")
  step {
    Actor.actorOf(new TestService).start()
    server.start()
    server.blockUntilStarted()
    Thread.sleep(1000)
  }

  /*"The server" should {
    "properly deliver the response of the service endpoint" in {
      import cc.spray.http.{HttpRequest, HttpResponse}
      client.dispatch(HttpRequest(uri = "http://localhost:16242/abc")).get mustEqual
              HttpResponse(200, "GET|xyz")
    }
  }*/

  step {
    server.stop()
    server.blockUntilStopped()
  }*/

}
