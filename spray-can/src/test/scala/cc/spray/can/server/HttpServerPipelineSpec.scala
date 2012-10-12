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

package spray.can.server

import akka.testkit.TestActorRef
import akka.util.{Duration, Timeout}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{ActorSystem, Actor, Props}
import spray.can.{HttpCommand, HttpPipelineStageSpec}
import spray.io._
import spray.http._
import spray.util._
import HttpHeaders.RawHeader


class HttpServerPipelineSpec extends Specification with HttpPipelineStageSpec {
  implicit val system: ActorSystem = ActorSystem()

  "The HttpServer pipeline" should {

    "dispatch a simple HttpRequest to a singleton service actor" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, message, _)) = process(Received(simpleRequest))
        message === HttpRequest(headers = List(RawHeader("host", "test.com")))
      }
    }

    "dispatch a fragmented HttpRequest" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, message, _)) = process(
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
        )
        message === HttpRequest(headers = List(RawHeader("host", "test.com")))
      }
    }

    "produce an error upon stray responses" in {
      singleHandlerPipeline.test {
        process(HttpCommand(HttpResponse())) must throwAn[IllegalStateException]
      }
    }

    "render a matched HttpResponse" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, message, peer)) = processAndClear(Received(simpleRequest))
        message === HttpRequest(headers = List(RawHeader("host", "test.com")))
        peer.tell(HttpCommand(HttpResponse()), sender1)
        result.commands(0) === SendString(simpleResponse)
      }
    }

    "dispatch requests to the right service actor when using per-connection handlers" in {
      val counter = new AtomicInteger
      def createHandler(ctx: PipelineContext) =
        ctx.connectionActorContext.actorOf(Props(new DummyActor), "actor" + counter.incrementAndGet())
      testPipeline(PerConnectionHandler(createHandler)).test {
        val Commands(
          Tell(ActorPathName("actor1"), _, _),
          Tell(ActorPathName("actor1"), _, _)
        ) = process(
          Received(simpleRequest),
          Received(simpleRequest)
        )
        success
      }
    }

    "dispatch requests to the right service actor when using per-message handlers" in {
      val counter = new AtomicInteger
      def createHandler(ctx: PipelineContext) =
        ctx.connectionActorContext.actorOf(Props(new DummyActor), "actr" + counter.incrementAndGet())
      testPipeline(PerMessageHandler(createHandler)).test {
        val Commands(
          Tell(ActorPathName("actr1"), _, _),
          Tell(ActorPathName("actr2"), _, _),
          Tell(ActorPathName("actr3"), _, _),
          Tell(ActorPathName("actr3"), _, _),
          Tell(ActorPathName("actr3"), _, _),
          Tell(ActorPathName("actr3"), _, _),
          Tell(ActorPathName("actr4"), _, _)
        ) = process(
          Received(simpleRequest),
          Received(simpleRequest),
          Received(chunkedRequestStart),
          Received(messageChunk),
          Received(messageChunk),
          Received(chunkedMessageEnd),
          Received(chunkedRequestStart)
        )
        success
      }
    }

    "dispatch SentAck messages" in {
      "to the sender of an HttpResponse" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(HttpResponse().withSentAck(1)), sender1)
          val Commands(Tell(receiver, 1, _)) = clearAndProcess(AckEventWithReceiver(1, sender1))
          receiver === sender1
        }
      }
      "to the senders of a ChunkedResponseStart, MessageChunk and ChunkedMessageEnd" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = processAndClear(Received(simpleRequest))
          peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse()).withSentAck(1)), sender1)
          process(AckEventWithReceiver(1, sender1))
          peer.tell(HttpCommand(MessageChunk("part 1").withSentAck(2)), sender2)
          process(AckEventWithReceiver(2, sender2))
          peer.tell(HttpCommand(MessageChunk("part 2")), sender2)
          peer.tell(HttpCommand(MessageChunk("part 3").withSentAck(3)), sender3)
          peer.tell(HttpCommand(ChunkedMessageEnd().withSentAck(4)), sender4)
          val Commands(commands@ _*) = process(AckEventWithReceiver(3, sender3), AckEventWithReceiver(4, sender4))

          commands(0) === SendString(`chunkedResponseStart`)
          val Tell(`sender1`, 1, _) = commands(1)
          commands(2) === SendString(prep("6\npart 1\n"))
          val Tell(`sender2`, 2, _) = commands(3)
          commands(4) === SendString(prep("6\npart 2\n"))
          commands(5) === SendString(prep("6\npart 3\n"))
          commands(6) === SendString(prep("0\n\n"))
          val Tell(`sender3`, 3, _) = commands(7)
          val Tell(`sender4`, 4, _) = commands(8)
          success
        }
      }
    }

    "dispatch Closed messages" in {
      val CLOSED = Closed(`testHandle`, PeerClosed)
      "to the handler if no request is open" in {
        singleHandlerPipeline.test {
          val Commands(Tell(receiver, CLOSED, _)) = process(CLOSED)
          receiver === singletonHandler
        }
      }
      "to the handler if a request is open" in {
        singleHandlerPipeline.test {
          processAndClear(Received(simpleRequest))
          val Commands(Tell(receiver, CLOSED, _)) = process(CLOSED)
          receiver === singletonHandler
        }
      }
      "to the response sender if a response has been sent but not yet confirmed" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(HttpResponse().withSentAck(42)), sender1)
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === sender1
        }
      }
      "to the handler if a response has been sent and confirmed" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(HttpResponse().withSentAck(42)), sender1)
          process(AckEventWithReceiver(42, sender1))
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === singletonHandler
        }
      }
      "to the handler if a response has been sent without ack" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(HttpResponse()), sender1)
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === singletonHandler
        }
      }
      "to the response sender of a chunk stream if a chunk has been sent but not yet confirmed" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse()).withSentAck(12)), sender1)
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === sender1
        }
      }
      "to the last response sender of a chunk stream if a chunk has been sent and confirmed" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse())), sender1)
          peer.tell(HttpCommand(MessageChunk("bla").withSentAck(12)), sender2)
          process(AckEventWithReceiver(12, sender2))
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === sender2
        }
      }
      "to the last response sender if a final chunk has been sent but not yet confirmed" in {
        singleHandlerPipeline.test {
          val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
          peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse()).withSentAck(1)), sender1)
          peer.tell(HttpCommand(MessageChunk("bla")), sender2)
          peer.tell(HttpCommand(ChunkedMessageEnd().withSentAck(2)), sender3)
          process(AckEventWithReceiver(2, sender3))
          val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
          receiver === sender3
        }
      }
      "to the handler if a final chunk has been sent and no confirmation is open" in {
        "example 1" in {
          singleHandlerPipeline.test {
            val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
            peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse())), sender1)
            peer.tell(HttpCommand(MessageChunk("bla")), sender2)
            peer.tell(HttpCommand(ChunkedMessageEnd()), sender3)
            val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
            receiver === singletonHandler
          }
        }
        "example 1" in {
          singleHandlerPipeline.test {
            val Commands(Tell(_, _, peer)) = process(Received(simpleRequest))
            peer.tell(HttpCommand(ChunkedResponseStart(HttpResponse()).withSentAck(1)), sender1)
            peer.tell(HttpCommand(MessageChunk("bla")), sender2)
            peer.tell(HttpCommand(ChunkedMessageEnd().withSentAck(2)), sender3)
            process(AckEventWithReceiver(1, sender1), AckEventWithReceiver(2, sender3))
            val Commands(Tell(receiver, CLOSED, _)) = clearAndProcess(CLOSED)
            receiver === singletonHandler
          }
        }
      }
    }

    "handle 'Expected: 100-continue' headers" in {
      def example(expectValue: String) = {
        singleHandlerPipeline.test {
          val Commands(message, Tell(`singletonHandler`, request, peer)) = processAndClear {
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
            }
          }
          peer.tell(HttpCommand(HttpResponse()), sender1)

          message === SendString("HTTP/1.1 100 Continue\r\n\r\n")
          request === HttpRequest(
            headers = List(
              RawHeader("expect", expectValue),
              RawHeader("content-length", "12"),
              RawHeader("content-type", "text/plain"),
              RawHeader("host", "test.com")
            )
          ).withEntity("bodybodybody")
          result.commands(0) === SendString(simpleResponse)
        }
      }
      "with a header value fully matching the spec" in example("100-continue")
      "with a header value containing illegal casing" in example("100-Continue")
    }

    "dispatch HEAD requests as GET requests (and suppress sending of their bodies)" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, request, peer)) = processAndClear {
          Received {
            prep {
              """|HEAD / HTTP/1.1
                |Host: test.com
                |
                |"""
            }
          }
        }
        peer.tell(HttpCommand(HttpResponse(entity = "1234567")), sender1)

        request === HttpRequest(headers = List(RawHeader("host", "test.com")))
        result.commands(0) === SendString {
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

    "dispatch Timeout messages in case of a request timeout (and dispatch respective response)" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, _, peer)) = processAndClear(Received(simpleRequest))
        Thread.sleep(50)
        val Commands(Tell(`singletonHandler`, spray.http.Timeout(_), `peer`)) = processAndClear(TickGenerator.Tick)
        peer.tell(HttpCommand(HttpResponse()), sender1)
        result.commands(0) === SendString(simpleResponse)
      }
    }

    "dispatch the default timeout response if the Timeout timed out" in {
      singleHandlerPipeline.test {
        val Commands(Tell(`singletonHandler`, _, peer)) = processAndClear(Received(simpleRequest))
        Thread.sleep(55)
        val Commands(Tell(`singletonHandler`, spray.http.Timeout(_), `peer`)) = processAndClear(TickGenerator.Tick)
        Thread.sleep(35)
        val Commands(message, HttpServer.Close(CleanClose)) = process(TickGenerator.Tick)
        message === SendString {
          prep {
            """|HTTP/1.1 500 Internal Server Error
               |Connection: close
               |Server: spray/1.0
               |Date: XXXX
               |Content-Type: text/plain
               |Content-Length: 13
               |
               |Timeout for /"""
          }
        }
      }
    }
  }

  step(system.shutdown())

  ///////////////////////// SUPPORT ////////////////////////

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

  val connectionActor = TestActorRef(new DummyActor, "connectionActor")
  val singletonHandler = TestActorRef(new DummyActor, "singletonHandler")

  class DummyActor extends Actor {
    def receive = {
      case x: Command => currentTestPipelines.commandPipeline(x)
      case x: Event => currentTestPipelines.eventPipeline(x)
    }
    def getContext = context
  }

  override def connectionActorContext = connectionActor.underlyingActor.getContext

  val singleHandlerPipeline = testPipeline(SingletonHandler(singletonHandler))

  def testPipeline(messageHandler: MessageHandler) = HttpServer.pipeline(
    new ServerSettings(
      ConfigFactory.parseString("""
        spray.can.server.server-header = spray/1.0
        spray.can.server.idle-timeout = 150 ms
        spray.can.server.request-timeout = 50 ms
        spray.can.server.timeout-timeout = 30 ms
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

