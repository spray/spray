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

import akka.event.LoggingAdapter
import cc.spray.io._
import parsing.{ParserSettings, ErrorState, EmptyResponseParser}

object ResponseParsing {

  def apply(settings: ParserSettings, log: LoggingAdapter) = new EventPipelineStage {
    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      new MessageParsingPipelines(settings, commandPL, eventPL) {
        lazy val startParser = new EmptyResponseParser(settings)

        def handleParseError(state: ErrorState) {
          log.warning("Received illegal response: {}", state.message)
          commandPL(IoPeer.Close(ProtocolError(state.message)))
        }
      }
    }
  }
}