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
import nio.{Received, ProtocolError, Close, Pipelines}
import parsing.{ErrorState, EmptyResponseParser}
import util.Logging

object HttpResponseParsing extends Logging {

  def apply(config: HttpParserConfig)(thePipelines: Pipelines) = {
    val piplelineStage = new HttpMessageParsingPipelineStage {
      val startParser = new EmptyResponseParser(config)
      def parserConfig = config
      def pipelines = thePipelines
      def handleParseError(state: ErrorState) {
        log.warn("Received illegal response: {}", state.message)
        pipelines.downstream(Close(pipelines.handle, ProtocolError(state.message)))
      }
    }
    thePipelines.withUpstream {
      case x: Received => piplelineStage(x.buffer)
      case event => thePipelines.upstream(event)
    }
  }
}