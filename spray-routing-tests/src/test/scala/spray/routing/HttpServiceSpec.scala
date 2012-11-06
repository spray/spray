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
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import spray.httpx.RequestBuilding
import spray.http._
import MediaTypes._
import HttpCharsets._


class HttpServiceSpec extends TestKit(ActorSystem("HttpServiceSpec")) with Specification with RequestBuilding with ImplicitSender {
  sequential

  class RootService(subRouteActor: ActorRef) extends Actor with HttpServiceActor {
    def receive = runRoute {
      path("") {
        complete("yeah")
      } ~
      pathPrefix("sub") {
        subRouteActor ! _
      }
    }
  }

  class SubService extends Actor with HttpServiceActor {
    def receive = runRoute {
      path(Rest) { pathRest =>
        complete(pathRest)
      }
    }
  }

  "The 'runRoute' directive" should {
    val subActor = system.actorOf(Props(new SubService))
    val rootActor = system.actorOf(Props(new RootService(subActor)))

    "properly produce HttpResponses from the root actor" in {
      rootActor ! Get("/")
      receiveOne(remaining) === HttpResponse(entity = HttpBody(ContentType(`text/plain`, `ISO-8859-1`), "yeah"))
    }

    "properly produce HttpResponses from a sub actor" in {
      rootActor ! Get("/sub/abc")
      receiveOne(remaining) === HttpResponse(entity = HttpBody(ContentType(`text/plain`, `ISO-8859-1`), "abc"))
    }
  }

  step(system.shutdown())

}