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

import config.HttpServerConfig
import model.{HttpResponse, HttpHeader, HttpRequest}
import akka.actor.Props
import akka.pattern.ask
import akka.util.{Duration, Timeout}
import cc.spray.io.pipelines.{PerMessageHandler, PerConnectionHandler, SingletonHandler, MessageHandler}
import java.util.concurrent.atomic.AtomicInteger
import org.specs2.specification.Step

class HttpServerPipelineSpec extends PipelineSpec("HttpServerPipelineSpec") { def is =

  "The HttpServerPipeline should"^
    "dispatch a simple HttpRequest to a singleton service actor" ! dispatchSimpleRequestToSingletonHandler^
    "correctly dispatch a fragmented HttpRequest" ! dispatchFragmentedRequest^
    "produce an error upon stray responses" ! strayResponse^
    "correctly render a matched HttpResponse" ! renderResponse^
    "dispatch requests to the right service actor when using per-connection handlers" ! perConnectionHandlers^
    "dispatch requests to the right service actor when using per-message handlers" ! perMessageHandlers^
  Step(stop())

  def dispatchSimpleRequestToSingletonHandler = {
    singletonPipeline.runEvents {
      received(simpleRequest)
    } must produceOneCommand {
      HttpServer.Dispatch(singletonHandler, HttpRequest(headers = List(HttpHeader("host", "test.com"))))
    }
  }

  def dispatchFragmentedRequest = {
    singletonPipeline.runEvents(
      received {
        """|GET / HTTP/1.1
           |Host: te"""
      },
      received {
        """|st.com
           |
           |"""
      }
    ) must produceOneCommand {
      HttpServer.Dispatch(singletonHandler, HttpRequest(headers = List(HttpHeader("host", "test.com"))))
    }
  }

  def strayResponse = {
    singletonPipeline.runCommands(HttpResponse()) must throwAn[IllegalStateException]
  }

  def renderResponse = {
    val pipeline = singletonPipeline
    pipeline.runEvents(received(simpleRequest))
    pipeline.runCommands(HttpResponse()) must produceOneCommand(send(simpleResponse))
  }

  def perConnectionHandlers = {
    val pipeline1 = testPipeline(PerConnectionHandler(Props(new DummyActor('actor1))))
    val pipeline2 = testPipeline(PerConnectionHandler(Props(new DummyActor('actor2))))
    def receiver(pipeline: TestPipeline) = dispatchReceiverName(pipeline.runEvents(received(simpleRequest)));
    { receiver(pipeline1) === 'actor1 } and
    { receiver(pipeline2) === 'actor2 } and
    { receiver(pipeline1) === 'actor1 } and
    { receiver(pipeline2) === 'actor2 }
  }

  def perMessageHandlers = {
    val counter = new AtomicInteger
    val pipeline = testPipeline(PerMessageHandler(Props(new DummyActor("actor" + counter.incrementAndGet()))))
    def receiver(msg: String) = dispatchReceiverName(pipeline.runEvents(received(msg)));
    { receiver(simpleRequest) === 'actor1 } and
    { receiver(simpleRequest) === 'actor2 } and
    { receiver(chunkedRequestStart) === 'actor3 } and
    { receiver(requestChunk) === 'actor3 } and
    { receiver(requestChunk) === 'actor3 } and
    { receiver(chunkedRequestEnd) === 'actor3 } and
    { receiver(chunkedRequestStart) === 'actor4 }
  }

  /////////////////////////// SUPPORT ////////////////////////////////

  implicit val timeout: Timeout = Duration("500 ms")

  val simpleRequest = """|GET / HTTP/1.1
                         |Host: test.com
                         |
                         |"""

  val simpleResponse =  """|HTTP/1.1 200 OK
                           |Content-Length: 0
                           |
                           |"""

  val chunkedRequestStart = """|GET / HTTP/1.1
                               |Host: test.com
                               |Transfer-Encoding: chunked
                               |
                               |"""

  val requestChunk = """|7
                        |body123
                        |"""

  val chunkedRequestEnd = """|0
                             |Age: 30
                             |Cache-Control: public
                             |
                             |"""

  def singletonPipeline = testPipeline(SingletonHandler(singletonHandler))

  def dispatchReceiverName(pipelineResult: TestPipelineResult) = {
    val (List(HttpServer.Dispatch(receiver, _)), _) = pipelineResult
    receiver.ask('name).get
  }

  def testPipeline(messageHandler: MessageHandler) = new TestPipeline(
    HttpServer.pipeline(
      HttpServerConfig(serverHeader = "test/no-date", idleTimeout = Duration("50 ms")),
      messageHandler,
      log
    )
  )
}
