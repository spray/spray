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
import akka.actor.ActorSystem
import cc.spray.can.parsing.ParserSettings
import cc.spray.can.{HttpEvent, HttpPipelineStageSpec}
import cc.spray.can.rendering.HttpRequestPartRenderingContext
import cc.spray.io.{ProtocolError, Event}


class ResponseParsingSpec extends Specification with HttpPipelineStageSpec {
  val system = ActorSystem()
  val fixture = new Fixture(ResponseParsing(new ParserSettings(), system.log))

  "The ResponseParsing PipelineStage" should {
    "be transparent to unrelated events" in {
      val ev = new Event {}
      fixture(ev).checkResult {
        event === ev
      }
    }
    "parse a simple response and produce the corresponding event" in {
      fixture(
        HttpRequestPartRenderingContext(request(), "localhost", 80),
        ClearCommandAndEventCollectors,
        Received(rawResponse("foo"))
      ).checkResult {
        event === HttpEvent(response("foo"))
      }
    }
    "parse a double response and produce the corresponding events" in {
      fixture(
        HttpRequestPartRenderingContext(request(), "localhost", 80),
        HttpRequestPartRenderingContext(request(), "localhost", 80),
        ClearCommandAndEventCollectors,
        Received(rawResponse("foo") + rawResponse("bar"))
      ).checkResult {
        events(0) === HttpEvent(response("foo"))
        events(1) === HttpEvent(response("bar"))
      }
    }
    "trigger an error on unmatched responses" in {
      "example 1" in {
        fixture(Received(rawResponse("foo"))).checkResult {
          command === HttpClient.Close(ProtocolError("Response to non-existent request"))
        }
      }
      "example 2" in {
        fixture(
          HttpRequestPartRenderingContext(request(), "localhost", 80),
          HttpRequestPartRenderingContext(request(), "localhost", 80),
          ClearCommandAndEventCollectors,
          Received(rawResponse("foo")),
          Received(rawResponse("bar")),
          Received(rawResponse("baz"))
        ).checkResult {
          command === HttpClient.Close(ProtocolError("Response to non-existent request"))
          events(0) === HttpEvent(response("foo"))
          events(1) === HttpEvent(response("bar"))
        }
      }
    }
  }

  step(system.shutdown())
}
