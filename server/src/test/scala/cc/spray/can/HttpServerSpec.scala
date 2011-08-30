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

class HttpServerSpec extends Specification {

  val h = new dispatch.nio.Http
  class TestService extends Actor {
    self.id = "testEndpoint"
    protected def receive = {
      case RequestContext(HttpRequest(method, uri, _, _, _, _), complete) => complete {
        HttpResponse(200, List(HttpHeader("Content-Type", "text/plain")),(method + "|" + uri).getBytes("ISO-8859-1"))
      }
    }
  }

  textFragment("This specification starts a new HttpServer and fires a few test requests against it")
  step {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(SimpleConfig(port = 16242, serviceActorId = "testEndpoint", requestTimeout = 0))).start()
  }

  "The server" should {
    import dispatch._
    "properly deliver the response of the service endpoint" in {
      val response = h(url("http://localhost:16242/abc") as_str)
      response() mustEqual "GET|/abc"
    }
    "properly deliver the response of the service endpoint" in {
      val response = h(url("http://localhost:16242/xyz") as_str)
      response() mustEqual "GET|/xyz"
    }
  }

  step {
    Actor.registry.shutdownAll()
    h.shutdown()
  }

}
