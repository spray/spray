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

package spray.can.server

import spray.can.rendering.HttpResponsePartRenderingContext
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.can.HttpEvent
import spray.util.ConnectionCloseReasons.ProtocolError
import spray.io._
import spray.http._

object RequestChunkAggregation {

  def apply(limit: Int): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var startEvent: HttpMessageStartEvent = _
          var request: HttpRequest = _
          var bb: BufferBuilder = _
          var closed = false

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case ev @ HttpMessageStartEvent(ChunkedRequestStart(req), _) ⇒ if (!closed) {
              startEvent = ev
              request = req
              if (req.entity.buffer.length <= limit) bb = BufferBuilder(req.entity.buffer)
              else closeWithError()
            }

            case HttpEvent(MessageChunk(body, _)) ⇒ if (!closed) {
              assert(bb != null)
              if (bb.size + body.length <= limit) bb.append(body)
              else closeWithError()
            }

            case HttpEvent(_: ChunkedMessageEnd) ⇒ if (!closed) {
              assert(startEvent != null && request != null && bb != null)
              val entity = request.entity.map((ct, _) ⇒ ct -> bb.toArray)
              eventPL(startEvent.copy(messagePart = request.copy(entity = entity)))
              startEvent = null
              request = null
              bb = null
            }

            case ev ⇒ eventPL(ev)
          }

          def closeWithError() {
            val msg = "Aggregated request entity greater than configured limit of " + limit + " bytes"
            commandPL(HttpResponsePartRenderingContext(HttpResponse(StatusCodes.RequestEntityTooLarge, msg)))
            commandPL(HttpServer.Close(ProtocolError(msg)))
            closed = true
          }
        }
    }
}