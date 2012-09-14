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

import org.specs2.mutable.Specification
import com.typesafe.config.ConfigFactory
import akka.testkit.TestActorRef
import akka.actor.{ActorSystem, Actor}
import cc.spray.can.{HttpCommand, HttpPipelineStageSpec}
import cc.spray.io.IOPeer
import cc.spray.http._


class HttpClientPipelineSpec extends Specification with HttpPipelineStageSpec {
  implicit val system = ActorSystem()

  "The HttpClient pipeline" should {

    "send out a simple HttpRequest to the server" in {
      fixture(
        HttpCommand(request()) from sender1
      ).checkResult {
        command === SendString(emptyRawRequest())
      }
    }

    "dispatch an incoming HttpResponse back to the sender" in {
      fixture(
        HttpCommand(request()) from sender1,
        Received(rawResponse)
      ).checkResult {
        commands(0) === SendString(emptyRawRequest())
        commands(1) === IOPeer.Tell(sender1, response, connectionActor)
      }
    }

    "properly complete a 3 requests pipelined dialog" in {
      fixture(
        HttpCommand(request("Request 1")) from sender1,
        HttpCommand(request("Request 2")) from sender2,
        HttpCommand(request("Request 3")) from sender1,
        Received(rawResponse("Response 1")),
        Received(rawResponse("Response 2")),
        Received(rawResponse("Response 3"))
      ).checkResult {
        commands(0) === SendString(rawRequest("Request 1"))
        commands(1) === SendString(rawRequest("Request 2"))
        commands(2) === SendString(rawRequest("Request 3"))
        commands(3) === IOPeer.Tell(sender1, response("Response 1"), connectionActor)
        commands(4) === IOPeer.Tell(sender2, response("Response 2"), connectionActor)
        commands(5) === IOPeer.Tell(sender1, response("Response 3"), connectionActor)
      }
    }

    "properly handle responses to HEAD requests" in {
      fixture(
        HttpCommand(HttpRequest(method = HttpMethods.HEAD)) from sender1,
        Received {
          prep {
            """|HTTP/1.1 200 OK
               |Server: spray/1.0
               |Date: Thu, 25 Aug 2011 09:10:29 GMT
               |Content-Length: 8
               |Content-Type: text/plain
               |
               |"""
          }
        }
      ).checkResult {
        commands(0) === SendString(emptyRawRequest(method = "HEAD"))
        commands(1) === IOPeer.Tell(sender1, response("12345678").withEntity(""), connectionActor)
      }
    }
  }

  step(system.shutdown())

  /////////////////////////// SUPPORT ////////////////////////////////

  val connectionActor = TestActorRef(new NamedActor("connectionActor"))

  class NamedActor(val name: String) extends Actor {
    def receive = { case 'name => sender ! name}
    def getContext = context
  }

  def fixture: Fixture = {
    new Fixture(testPipeline) {
      override def getConnectionActorContext = connectionActor.underlyingActor.getContext
    }
  }

  def testPipeline = HttpClient.pipeline(
    new ClientSettings(
      ConfigFactory.parseString("""
        spray.can.client.user-agent-header = spray/1.0
        spray.can.client.idle-timeout = 50 ms
        spray.can.client.reaping-cycle = 0  # don't enable the TickGenerator
      """)
    ),
    system.log
  )

}
