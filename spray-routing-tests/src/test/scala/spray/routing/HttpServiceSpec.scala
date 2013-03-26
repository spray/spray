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

package spray.routing

import org.specs2.mutable.Specification
import akka.testkit.TestProbe
import akka.actor._
import spray.httpx.RequestBuilding._
import spray.util._
import spray.http._
import MediaTypes._
import HttpCharsets._


class HttpServiceSpec extends Specification {

  class RootService(subRouteActor: ActorRef) extends HttpServiceActor {
    def receive = runRoute {
      path("") {
        complete("yeah")
      } ~
      pathPrefix("sub") {
        subRouteActor ! _
      }
    }
  }

  class SubService extends HttpServiceActor {
    def receive = runRoute {
      path(Rest) { pathRest =>
        complete(pathRest)
      }
    }
  }

  val system = ActorSystem(Utils.actorSystemNameFrom(getClass))
  val subActor = system.actorOf(Props(new SubService))
  val rootActor = system.actorOf(Props(new RootService(subActor)))

  "The 'runRoute' directive" should {

    "properly produce HttpResponses from the root actor" in {
      val probe = TestProbe()(system)
      probe.send(rootActor, Get("/"))
      probe.expectMsg(HttpResponse(entity = HttpBody(ContentType(`text/plain`, `UTF-8`), "yeah")))
      success
    }

    "properly produce HttpResponses from a sub actor" in {
      val probe = TestProbe()(system)
      probe.send(rootActor, Get("/sub/abc"))
      probe.expectMsg(HttpResponse(entity = HttpBody(ContentType(`text/plain`, `UTF-8`), "abc")))
      success
    }
  }

  step(system.shutdown())
}