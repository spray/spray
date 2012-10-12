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
import akka.actor.ActorSystem
import spray.can.parsing.ParserSettings
import spray.can.{HttpEvent, HttpPipelineStageSpec}
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.util.ProtocolError
import spray.io.Event


class ResponseParsingSpec extends Specification with HttpPipelineStageSpec {
  val system = ActorSystem()
  val pipelineStage = ResponseParsing(new ParserSettings(), system.log)

  "The ResponseParsing PipelineStage" should {
    "be transparent to unrelated events" in {
      val ev = new Event {}
      pipelineStage.test {
        val Events(event) = process(ev)
        event === ev
      }
    }
    "parse a simple response and produce the corresponding event" in {
      pipelineStage.test {
        process(HttpRequestPartRenderingContext(request(), "localhost", 80))
        val Events(event) = process(Received(rawResponse("foo")))
        event === HttpEvent(response("foo"))
      }
    }
    "parse a double response and produce the corresponding events" in {
      pipelineStage.test {
        val Events(events@ _*) = process(
          HttpRequestPartRenderingContext(request(), "localhost", 80),
          HttpRequestPartRenderingContext(request(), "localhost", 80),
          Received(rawResponse("foo") + rawResponse("bar"))
        )
        events(0) === HttpEvent(response("foo"))
        events(1) === HttpEvent(response("bar"))
      }
    }
    "trigger an error on unmatched responses" in {
      "example 1" in {
        pipelineStage.test {
          val Commands(command) = process(Received(rawResponse("foo")))
          command === HttpClient.Close(ProtocolError("Response to non-existent request"))
        }
      }
      "example 2" in {
        pipelineStage.test {
          process(
            HttpRequestPartRenderingContext(request(), "localhost", 80),
            HttpRequestPartRenderingContext(request(), "localhost", 80)
          )
          val ProcessResult(commands, events) = clearAndProcess(
            Received(rawResponse("foo")),
            Received(rawResponse("bar")),
            Received(rawResponse("baz"))
          )
          commands(0) === HttpClient.Close(ProtocolError("Response to non-existent request"))
          events(0) === HttpEvent(response("foo"))
          events(1) === HttpEvent(response("bar"))
        }
      }
    }
  }

  step(system.shutdown())
}
