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

package cc.spray.can.server

import cc.spray.can.model.{HttpHeader, HttpResponse}
import cc.spray.can.parsing._
import cc.spray.can.rendering.HttpResponsePartRenderingContext
import cc.spray.io._
import akka.event.LoggingAdapter

object RequestParsing {

  def apply(settings: ParserSettings, log: LoggingAdapter): EventPipelineStage = new EventPipelineStage {

    def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): EPL = {

      new MessageParsingPipelines(settings, commandPL, eventPL) {
        val startParser = new EmptyRequestParser(settings)
        currentParsingState = startParser

        def handleParseError(state: ErrorState) {
          log.warning("Illegal request, responding with status {} and '{}'", state.status, state.message)
          val response = HttpResponse(
            status = state.status,
            headers = List(HttpHeader("Content-Type", "text/plain"))
          ).withBody(state.message)

          // In case of a request parsing error we probably stopped reading the request somewhere in between,
          // where we cannot simply resume. Resetting to a known state is not easy either,
          // so we need to close the connection to do so.
          commandPL(HttpResponsePartRenderingContext(response))
          commandPL(HttpServer.Close(ProtocolError(state.message)))
        }
      }
    }
  }

}