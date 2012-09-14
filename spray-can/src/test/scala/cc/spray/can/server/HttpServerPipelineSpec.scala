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

package cc.spray.can.server

import akka.pattern.ask
import akka.testkit.TestActorRef
import akka.util.{Duration, Timeout}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{ActorSystem, Actor, Props}
import cc.spray.can.{HttpCommand, HttpPipelineStageSpec}
import cc.spray.io.pipelining.MessageHandlerDispatch._
import cc.spray.util._
import cc.spray.io._
import cc.spray.http._
import HttpHeaders.RawHeader


class HttpServerPipelineSpec extends Specification with HttpPipelineStageSpec {
  implicit val system = ActorSystem()

  "The HttpServer pipeline" should {

    "dispatch a simple HttpRequest to a singleton service actor" in {
      singleHandlerFixture {
        Received(simpleRequest)
      }.checkResult {
        command.asTell.receiver === singletonHandler
        command.asTell.message === HttpRequest(headers = List(RawHeader("host", "test.com")))
      }
    }

    "correctly dispatch a fragmented HttpRequest" in {
      singleHandlerFixture(
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
      ).checkResult {
        command.asTell.receiver === singletonHandler
        command.asTell.message === HttpRequest(headers = List(RawHeader("host", "test.com")))
      }
    }

    "produce an error upon stray responses" in {
      singleHandlerFixture(
        HttpCommand(HttpResponse())
      ) must throwAn[IllegalStateException]
    }

    "correctly render a matched HttpResponse" in {
      singleHandlerFixture(
        Received(simpleRequest),
        HttpCommand(HttpResponse()) from sender1
      ).checkResult {
        commands(0).asTell.receiver === singletonHandler
        commands(0).asTell.message === HttpRequest(headers = List(RawHeader("host", "test.com")))
        commands(1) === SendString(simpleResponse)
      }
    }

    "dispatch requests to the right service actor when using per-connection handlers" in {
      val counter = new AtomicInteger
      fixture(PerConnectionHandler(_ => Props(new NamedActor("actor" + counter.incrementAndGet())))).apply(
        Received(simpleRequest),
        Received(simpleRequest)
      ).checkResult {
        commands.map {
          case IOServer.Tell(receiver, _, _) => SendString(receiver.ask('name).mapTo[String].await)
        } === Seq(
          SendString("actor1"),
          SendString("actor1") // dispatched to the same handler, since we are testing "one connection"
        )
      }
    }

    "dispatch requests to the right service actor when using per-message handlers" in {
      val counter = new AtomicInteger
      fixture(PerMessageHandler(_ => Props(new NamedActor("actor" + counter.incrementAndGet())))).apply(
        Received(simpleRequest),
        Received(simpleRequest),
        Received(chunkedRequestStart),
        Received(messageChunk),
        Received(messageChunk),
        Received(chunkedMessageEnd),
        Received(chunkedRequestStart)
      ).checkResult {
        commands.map {
          case IOServer.Tell(receiver, _, _) => SendString(receiver.ask('name).mapTo[String].await)
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

    "correctly dispatch SentOk messages" in {
      "to the sender of an HttpResponse" in {
        singleHandlerFixture(
          Received(simpleRequest),
          HttpCommand(HttpResponse()) from sender1,
          ClearCommandAndEventCollectors,
          IOBridge.SentOk(testHandle)
        ).checkResult {
          command.asTell.receiver === sender1
          command.asTell.message === IOServer.SentOk(testHandle)
        }
      }
      "to the senders of a ChunkedResponseStart, MessageChunk and ChunkedMessageEnd" in {
        singleHandlerFixture(
          Received(simpleRequest),
          ClearCommandAndEventCollectors,
          HttpCommand(ChunkedResponseStart(HttpResponse())) from sender1,
          IOBridge.SentOk(testHandle),
          HttpCommand(MessageChunk("part 1")) from sender2,
          IOBridge.SentOk(testHandle),
          HttpCommand(MessageChunk("part 2")) from sender3,
          HttpCommand(ChunkedMessageEnd()) from sender4,
          IOBridge.SentOk(testHandle),
          IOBridge.SentOk(testHandle)
        ).checkResult {
          commands(0) === SendString(chunkedResponseStart)
          commands(1).asTell.receiver === sender1
          commands(1).asTell.message === IOServer.SentOk(testHandle)
          commands(2) === SendString(prep("6\npart 1\n"))
          commands(3).asTell.receiver === sender2
          commands(3).asTell.message === IOServer.SentOk(testHandle)
          commands(4) === SendString(prep("6\npart 2\n"))
          commands(5) === SendString(prep("0\n\n"))
          commands(6).asTell.receiver === sender3
          commands(6).asTell.message === IOServer.SentOk(testHandle)
          commands(7).asTell.receiver === sender4
          commands(7).asTell.message === IOServer.SentOk(testHandle)
        }
      }
    }

    "correctly handle 'Expected: 100-continue' headers" in {
      def example(expectValue: String) = {
        singleHandlerFixture(
          Received {
            prep {
              """|GET / HTTP/1.1
                |Host: test.com
                |Content-Type: text/plain
                |Content-Length: 12
                |Expect: %s
                |
                |bodybodybody""".format(expectValue)
            }
          },
          HttpCommand(HttpResponse()) from sender1
        ).checkResult {
          commands(0) === SendString("HTTP/1.1 100 Continue\r\n\r\n")
          commands(1).asTell.receiver === singletonHandler
          commands(1).asTell.message === HttpRequest(
            headers = List(
              RawHeader("expect", expectValue),
              RawHeader("content-length", "12"),
              RawHeader("content-type", "text/plain"),
              RawHeader("host", "test.com")
            )
          ).withEntity("bodybodybody")
          commands(2) === SendString(simpleResponse)
        }
      }
      "with a header value fully matching the spec" in example("100-continue")
      "with a header value containing illegal casing" in example("100-Continue")
    }

    "dispatch HEAD requests as GET requests (and suppress sending of their bodies)" in {
      singleHandlerFixture(
        Received {
          prep {
            """|HEAD / HTTP/1.1
              |Host: test.com
              |
              |"""
          }
        },
        HttpCommand(HttpResponse(entity = "1234567")) from sender1
      ).checkResult {
        commands(0).asTell.receiver === singletonHandler
        commands(0).asTell.message === HttpRequest(headers = List(RawHeader("host", "test.com")))
        commands(1) === SendString {
          prep {
          """|HTTP/1.1 200 OK
             |Server: spray/1.0
             |Date: XXXX
             |Content-Type: text/plain
             |Content-Length: 7
             |
             |"""
          }
        }
      }
    }
  }

  step(system.shutdown())

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

  val chunkedResponseStart = prep {
  """|HTTP/1.1 200 OK
     |Transfer-Encoding: chunked
     |Server: spray/1.0
     |Date: XXXX
     |
     |"""
  }

  val messageChunk = prep {
  """|7
     |body123
     |"""
  }

  val chunkedMessageEnd = prep {
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

  def singleHandlerFixture = fixture(SingletonHandler(singletonHandler))

  def fixture(messageHandler: MessageHandler): Fixture = {
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
      """)
    ),
    messageHandler,
    req => HttpResponse(500, "Timeout for " + req.uri),
    Some(new StatsSupport.StatsHolder),
    system.log
  )

}
