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

import org.specs2.mutable.Specification
import parsing.ParserSettings
import cc.spray.io.Event

class ResponseParsingSpec extends Specification with HttpPipelineStageSpec {

  "The ResponseParsing PipelineStage" should {
    "be transparent to unrelated events" in {
      val event = new Event {}
      fixture(event).events === Seq(event)
    }
    "parse a simple response and produce the corresponding event" in {
      fixture(Received(rawResponse("foo"))).events === Seq(response("foo"))
    }
    "parse a double response and produce the corresponding events" in {
      fixture(Received(rawResponse("foo") + rawResponse("bar"))).commandsAndEvents === (
        Seq(),
        Seq(
          response("foo"),
          response("bar")
        )
      )
    }
  }

  step {
    cleanup()
  }

  /////////////////////////// SUPPORT ////////////////////////////////

  val fixture = new Fixture(ResponseParsing(new ParserSettings(), system.log))

}
