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
import akka.actor.{Actor, Props}
import cc.spray.can.{HttpCommand, HttpPipelineStageSpec}
import cc.spray.io.pipelining.MessageHandlerDispatch._
import cc.spray.util._
import cc.spray.io._
import cc.spray.http._
import HttpHeaders.RawHeader


class HttpServerPipelineSpec extends Specification with HttpPipelineStageSpec {

  "The HttpServer pipeline" should {

    "dispatch a simple HttpRequest to a singleton service actor" in {
      singleHandlerFixture(Received(simpleRequest)) must produce(
        commands = Seq(
          IoServer.Tell(singletonHandler, HttpRequest(headers = List(RawHeader("host", "test.com"))), IgnoreSender)
        ),
        ignoreTellSender = true
      )
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
      ) must produce(
        commands = Seq(
          IoServer.Tell(singletonHandler, HttpRequest(headers = List(RawHeader("host", "test.com"))), IgnoreSender)
        ),
        ignoreTellSender = true
      )
    }

    "produce an error upon stray responses" in {
      singleHandlerFixture(HttpCommand(HttpResponse())) must throwAn[IllegalStateException]
    }

    "correctly render a matched HttpResponse" in {
      singleHandlerFixture(
        Received(simpleRequest),
        HttpCommand(HttpResponse())
      ) must produce(
        commands = Seq(
          IoServer.Tell(singletonHandler, HttpRequest(headers = List(RawHeader("host", "test.com"))), IgnoreSender),
          SendString(simpleResponse)
        ),
        ignoreTellSender = true
      )
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
        Received(messageChunk),
        Received(messageChunk),
        Received(chunkedMessageEnd),
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

    "correctly dispatch AckSend messages" in {
      "to the sender of an HttpResponse" in {
        val actor = system.actorOf(Props(new NamedActor("someActor")))
        singleHandlerFixture(
          Received(simpleRequest),
          Message(HttpCommand(HttpResponse()), sender = actor),
          ClearCommandAndEventCollectors,
          IOBridge.AckSend(dummyHandle)
        ) must produce(
          commands = Seq(
            IoServer.Tell(actor, IoServer.AckSend(dummyHandle), IgnoreSender)
          ),
          ignoreTellSender = true
        )
      }
      "to the senders of a ChunkedResponseStart, MessageChunk and ChunkedMessageEnd" in {
        val actor1 = system.actorOf(Props(new NamedActor("actor1")))
        val actor2 = system.actorOf(Props(new NamedActor("actor2")))
        val actor3 = system.actorOf(Props(new NamedActor("actor3")))
        val actor4 = system.actorOf(Props(new NamedActor("actor4")))
        singleHandlerFixture(
          Received(simpleRequest),
          ClearCommandAndEventCollectors,
          Message(HttpCommand(ChunkedResponseStart(HttpResponse())), sender = actor1),
          IOBridge.AckSend(dummyHandle),
          Message(HttpCommand(MessageChunk("part 1")), sender = actor2),
          IOBridge.AckSend(dummyHandle),
          Message(HttpCommand(MessageChunk("part 2")), sender = actor3),
          Message(HttpCommand(ChunkedMessageEnd()), sender = actor4),
          IOBridge.AckSend(dummyHandle),
          IOBridge.AckSend(dummyHandle)
        ) must produce(
          commands = Seq(
            SendString(chunkedResponseStart),
            IoServer.Tell(actor1, IoServer.AckSend(dummyHandle), IgnoreSender),
            SendString(prep("6\npart 1\n")),
            IoServer.Tell(actor2, IoServer.AckSend(dummyHandle), IgnoreSender),
            SendString(prep("6\npart 2\n")),
            SendString(prep("0\n\n")),
            IoServer.Tell(actor3, IoServer.AckSend(dummyHandle), IgnoreSender),
            IoServer.Tell(actor4, IoServer.AckSend(dummyHandle), IgnoreSender)
          ),
          ignoreTellSender = true
        )
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
          HttpCommand(HttpResponse())
        ) must produce(
          commands = Seq(
            SendString("HTTP/1.1 100 Continue\r\n\r\n"),
            IoServer.Tell(
              singletonHandler,
              HttpRequest(
                headers = List(
                  RawHeader("expect", expectValue),
                  RawHeader("content-length", "12"),
                  RawHeader("content-type", "text/plain"),
                  RawHeader("host", "test.com")
                )
              ).withEntity("bodybodybody"),
              IgnoreSender
            ),
            SendString(simpleResponse)
          ),
          ignoreTellSender = true
        )
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
        HttpCommand(HttpResponse(entity = "1234567"))
      ) must produce(
        commands = Seq(
          IoServer.Tell(singletonHandler, HttpRequest(headers = List(RawHeader("host", "test.com"))), IgnoreSender),
          SendString {
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
        ),
        ignoreTellSender = true
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
      """)
    ),
    messageHandler,
    req => HttpResponse(500, "Timeout for " + req.uri),
    Some(new StatsSupport.StatsHolder),
    system.log
  )

}
