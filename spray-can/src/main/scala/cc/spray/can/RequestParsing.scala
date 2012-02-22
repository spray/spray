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

package cc.spray
package can

import config.HttpParserConfig
import model.{HttpProtocols, HttpMethods, HttpHeader, HttpResponse}
import io._
import parsing.{ErrorState, EmptyRequestParser}
import rendering.HttpResponsePartRenderingContext
import akka.event.LoggingAdapter
import akka.actor.ActorContext

object RequestParsing {

  def apply(config: HttpParserConfig, log: LoggingAdapter) = new DoublePipelineStage {
    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      new MessageParsingPipelines(config, commandPL, eventPL) {
        lazy val startParser = new EmptyRequestParser(config)

        def handleParseError(state: ErrorState) {
          log.warning("Illegal request, responding with status {} and '{}'", state.status, state.message)
          val response = HttpResponse(
            status = state.status,
            headers = List(HttpHeader("Content-Type", "text/plain"))
          ).withBody(state.message)

          // In case of a request parsing error we probably stopped reading the request somewhere in between, where we
          // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
          // This is done here by pretending the request contained a "Connection: close" header
          commandPL {
            HttpResponsePartRenderingContext(response, HttpMethods.GET, HttpProtocols.`HTTP/1.1`, Some("close"))
          }
        }
      }
    }
  }

}