/*
 * Copyright (C) 2011-2013 spray.io
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
import akka.io.Tcp
import akka.util.ByteString
import spray.testkit.Specs2PipelineStageTest
import spray.can.rendering.RequestPartRenderingContext
import spray.http.HttpRequest
import spray.can.TestSupport._
import spray.can.Http

class ResponseParsingSpec extends Specification with Specs2PipelineStageTest {
  val stage = ResponseParsing(defaultParserSettings)

  "The ResponseParsing PipelineStage" should {

    "be transparent to unrelated events" in new Fixture(stage) {
      val ev = new Http.Event {}
      connectionActor ! ev
      events.expectMsg(ev)
    }

    "parse a simple response and produce the corresponding event" in new Fixture(stage) {
      connectionActor ! RequestPartRenderingContext(HttpRequest())
      connectionActor ! Tcp.Received(ByteString(rawResponse("foo")))
      events.expectMsg(Http.MessageEvent(response("foo")))
    }

    "parse a double response and produce the corresponding events" in new Fixture(stage) {
      connectionActor ! RequestPartRenderingContext(HttpRequest())
      connectionActor ! RequestPartRenderingContext(HttpRequest())
      connectionActor ! Tcp.Received(ByteString(rawResponse("foo") + rawResponse("bar")))
      events.expectMsg(Http.MessageEvent(response("foo")))
      events.expectMsg(Http.MessageEvent(response("bar")))
    }

    "trigger an error on unmatched responses" in {
      "example 1" in new Fixture(stage) {
        connectionActor ! Tcp.Received(ByteString(rawResponse("foo")))
        commands.expectMsg(Http.Close)
      }
      "example 2" in new Fixture(stage) {
        connectionActor ! RequestPartRenderingContext(HttpRequest())
        connectionActor ! RequestPartRenderingContext(HttpRequest())
        connectionActor ! Tcp.Received(ByteString(rawResponse("foo")))
        connectionActor ! Tcp.Received(ByteString(rawResponse("bar")))
        connectionActor ! Tcp.Received(ByteString(rawResponse("baz")))
        events.expectMsg(Http.MessageEvent(response("foo")))
        events.expectMsg(Http.MessageEvent(response("bar")))
        commands.receiveN(2) // ignore HttpRequestPartRenderingContexts
        commands.expectMsg(Http.Close)
      }
    }
  }
}
