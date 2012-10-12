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

import org.specs2.mutable.Specification
import com.typesafe.config.ConfigFactory
import akka.testkit.TestActorRef
import akka.actor.{ActorSystem, Actor}
import spray.can.{HttpCommand, HttpPipelineStageSpec}
import spray.io.IOPeer
import spray.util._
import spray.http._


class HttpClientPipelineSpec extends Specification with HttpPipelineStageSpec {
  implicit val system = ActorSystem()

  "The HttpClient pipeline" should {

    "send out a simple HttpRequest to the server" in {
      pipeline.test {
        val Commands(command) = process(HttpCommand(request()) from sender1)
        command === SendString(emptyRawRequest())
      }
    }

    "dispatch an incoming HttpResponse back to the sender" in {
      pipeline.test {
        val Commands(commands@ _*) = process(
          HttpCommand(request()) from sender1,
          Received(rawResponse)
        )
        commands(0) === SendString(emptyRawRequest())
        commands(1) === Tell(sender1, response, connectionActor)
      }
    }

    "properly complete a 3 requests pipelined dialog" in {
      pipeline.test {
        val Commands(commands@ _*) = process(
          HttpCommand(request("Request 1")) from sender1,
          HttpCommand(request("Request 2")) from sender2,
          HttpCommand(request("Request 3")) from sender1,
          Received(rawResponse("Response 1")),
          Received(rawResponse("Response 2")),
          Received(rawResponse("Response 3"))
        )
        commands(0) === SendString(rawRequest("Request 1"))
        commands(1) === SendString(rawRequest("Request 2"))
        commands(2) === SendString(rawRequest("Request 3"))
        commands(3) === Tell(sender1, response("Response 1"), connectionActor)
        commands(4) === Tell(sender2, response("Response 2"), connectionActor)
        commands(5) === Tell(sender1, response("Response 3"), connectionActor)
      }
    }

    "properly handle responses to HEAD requests" in {
      pipeline.test {
        val Commands(commands@ _*) = process(
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
        )
        commands(0) === SendString(emptyRawRequest(method = "HEAD"))
        commands(1) === Tell(sender1, response("12345678").withEntity(""), connectionActor)
      }
    }

    "properly parse and dispatch 'to-close' responses" in {
      pipeline.test {
        processAndClear(HttpCommand(request()) from sender1)
        val Commands(
          Tell(
            `sender1`,
            HttpResponse(StatusCodes.OK, HttpBody(ContentType.`application/octet-stream`, body), _, _),
            `connectionActor`
          )
        ) = process(
          Received {
            prep {
              """|HTTP/1.1 200 OK
                |Server: spray/1.0
                |Date: Thu, 25 Aug 2011 09:10:29 GMT
                |Connection: close
                |
                |Yeah"""
            }
          },
          IOPeer.Closed(testHandle, PeerClosed)
        )
        body.asString === "Yeah"
      }
    }
  }

  step(system.shutdown())

  /////////////////////////// SUPPORT ////////////////////////////////

  val connectionActor = TestActorRef(new NamedActor("connectionActor"))

  override def connectionActorContext = connectionActor.underlyingActor.getContext

  class NamedActor(val name: String) extends Actor {
    def receive = { case 'name => sender ! name}
    def getContext = context
  }

  val pipeline = HttpClient.pipeline(
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
