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
import cc.spray.io.{MessageHandler, SingletonHandler}
import model.{HttpResponse, HttpHeader, HttpRequest}

class HttpServerPipelineSpec extends PipelineSpec("HttpServerPipelineSpec") { def is =

  "The HttpServer should"^
    "dispatch a simple HttpRequest to a singleton service actor" ! dispatchSimpleRequestToSingletonHandler^
    "correctly dispatch a fragmented HttpRequest" ! dispatchFragmentedRequest^
    "produce an error upon stray responses" ! strayResponse^
    "correctly render a matched HttpResponse" ! renderResponse

  def dispatchSimpleRequestToSingletonHandler = {
    singletonPipeline.runEvents {
      received {
        """|GET / HTTP/1.1
           |Host: test.com
           |
           |"""
      }
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
    pipeline.runEvents {
      received {
        """|GET / HTTP/1.1
           |Host: test.com
           |
           |"""
      }
    }
    pipeline.clearResults()
    pipeline.runCommands(HttpResponse()) must produceOneCommand {
      send {
        """|HTTP/1.1 200 OK
           |Content-Length: 0
           |
           |"""
      }
    }
  }

  def singletonPipeline = testPipeline(SingletonHandler(singletonHandler))

  def testPipeline(messageHandler: MessageHandler) =
    new TestPipeline(HttpServer.pipeline(HttpServerConfig(serverHeader = "test/no-date"), messageHandler, log))
}
