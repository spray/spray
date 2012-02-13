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

import config.HttpParserConfig
import model.{HttpProtocols, HttpMethods, HttpHeader, HttpResponse}
import cc.spray.io.{Received, Pipelines}
import nio.{Received, Pipelines}
import parsing.{ErrorState, EmptyRequestParser}
import rendering.HttpResponsePartRenderingContext
import util.Logging

object HttpRequestParsing extends Logging {

  def apply(config: HttpParserConfig)(thePipelines: Pipelines) = {
    val piplelineStage = new HttpMessageParsingPipelineStage {
      val startParser = new EmptyRequestParser(config)
      def parserConfig = config
      def pipelines = thePipelines
      def handleParseError(state: ErrorState) {
        log.warn("Illegal request, responding with status {} and '{}'", state.status, state.message)
        val response = HttpResponse(
          status = state.status,
          headers = List(HttpHeader("Content-Type", "text/plain"))
        ).withBody(state.message)

        // In case of a request parsing error we probably stopped reading the request somewhere in between, where we
        // cannot simply resume. Resetting to a known state is not easy either, so we need to close the connection to do so.
        // This is done here by pretending the request contained a "Connection: close" header
        pipelines.downstream {
          HttpResponsePartRenderingContext(response, HttpMethods.GET, HttpProtocols.`HTTP/1.1`, Some("close"))
        }
      }
    }
    thePipelines.withUpstream {
      case x: Received => piplelineStage(x.buffer)
      case event => thePipelines.upstream(event)
    }
  }

}