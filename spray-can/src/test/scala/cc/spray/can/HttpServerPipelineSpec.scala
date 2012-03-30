/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import model.{HttpResponse, HttpHeader, HttpRequest}
import akka.pattern.ask
import akka.util.{Duration, Timeout}
import cc.spray.io.pipelines.MessageHandlerDispatch._
import java.util.concurrent.atomic.AtomicInteger
import cc.spray.util._
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import akka.testkit.TestActorRef
import akka.actor.{Actor, Props}
import cc.spray.io.IoServer

class HttpServerPipelineSpec extends Specification with HttpPipelineStageSpec {

  "The HttpServer pipeline" should {

    "dispatch a simple HttpRequest to a singleton service actor" in {
      singleHandlerFixture.apply(Received(simpleRequest)).commands.fixTells === Seq(
        IoServer.Tell(singletonHandler, HttpRequest(headers = List(HttpHeader("host", "test.com"))), null)
      )
    }

    "correctly dispatch a fragmented HttpRequest" in {
      singleHandlerFixture.apply(
        Received {
          prep {
          """|GET / HTTP/1.1
             |Host: te"""
          }
        },
        Received {
          prep {
          """|st.com
             |
             |"""
          }
        }
      ).commands.fixTells === Seq(
        IoServer.Tell(singletonHandler, HttpRequest(headers = List(HttpHeader("host", "test.com"))), null)
      )
    }

    "produce an error upon stray responses" in {
      singleHandlerFixture.apply(HttpResponse()) must throwAn[IllegalStateException]
    }

    "correctly render a matched HttpResponse" in {
      singleHandlerFixture(
        Received(simpleRequest),
        HttpResponse()
      ).commands.fixSends.last === SendString(simpleResponse)
    }

    "dispatch requests to the right service actor when using per-connection handlers" in {
      val counter = new AtomicInteger
      testFixture(PerConnectionHandler(_ => Props(new NamedActor("actor" + counter.incrementAndGet())))).apply(
        Received(simpleRequest),
        Received(simpleRequest)
      ).commands.map {
        case IoServer.Tell(receiver, _, _) => SendString(receiver.ask('name).mapTo[String].await)
      } === Seq(
        SendString("actor1"),
        SendString("actor1")
      )
    }

    "dispatch requests to the right service actor when using per-message handlers" in {
      val counter = new AtomicInteger
      testFixture(PerMessageHandler(_ => Props(new NamedActor("actor" + counter.incrementAndGet())))).apply(
        Received(simpleRequest),
        Received(simpleRequest),
        Received(chunkedRequestStart),
        Received(requestChunk),
        Received(requestChunk),
        Received(chunkedRequestEnd),
        Received(chunkedRequestStart)
      ).commands.map {
        case IoServer.Tell(receiver, _, _) => SendString(receiver.ask('name).mapTo[String].await)
      } === Seq(
        SendString("actor1"),
        SendString("actor2"),
        SendString("actor3"),
        SendString("actor3"),
        SendString("actor3"),
        SendString("actor3"),
        SendString("actor4")
      )
    }
  }

  /////////////////////////// SUPPORT ////////////////////////////////

  implicit val timeout: Timeout = Duration("500 ms")

  val simpleRequest = prep {
  """|GET / HTTP/1.1
     |Host: test.com
     |
     |"""
  }

  val simpleResponse = prep {
  """|HTTP/1.1 200 OK
     |Server: spray/1.0
     |Date: XXXX
     |Content-Length: 0
     |
     |"""
  }

  val chunkedRequestStart = prep {
  """|GET / HTTP/1.1
     |Host: test.com
     |Transfer-Encoding: chunked
     |
     |"""
  }

  val requestChunk = prep {
  """|7
     |body123
     |"""
  }

  val chunkedRequestEnd = prep {
  """|0
     |Age: 30
     |Cache-Control: public
     |
     |"""
  }

  val connectionActor = TestActorRef(new NamedActor("connectionActor"))
  val singletonHandler = TestActorRef(new NamedActor("singletonHandler"))

  class NamedActor(val name: String) extends Actor {
    def receive = { case 'name => sender ! name}
    def getContext = context
  }

  def singleHandlerFixture = testFixture(SingletonHandler(singletonHandler))

  def testFixture(messageHandler: MessageHandler): Fixture = {
    new Fixture(testPipeline(messageHandler)) {
      override def getConnectionActorContext = connectionActor.underlyingActor.getContext
    }
  }

  def testPipeline(messageHandler: MessageHandler) = HttpServer.pipeline(
    new ServerSettings(
      ConfigFactory.parseString("""
        spray.can.server.server-header = spray/1.0
        spray.can.server.idle-timeout = 50 ms
        spray.can.server.reaping-cycle = 0  # don't enable the TickGenerator
        spray.can.server.pipelining-limit = 10
        spray.can.server.request-chunk-aggregation-limit = 0 # disable chunk aggregation
      """),
      ConfirmedSends = true
    ),
    messageHandler,
    req => HttpResponse(500).withBody("Timeout for " + req.uri),
    system.log
  )

}
