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

import spray.can.HttpEvent
import spray.util.ProtocolError
import spray.http._
import spray.io._


object ResponseChunkAggregation {

  def apply(limit: Int): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var response: HttpResponse = _
          var bb: BufferBuilder = _
          var closed = false

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case HttpEvent(ChunkedResponseStart(res)) => if (!closed) {
              response = res
              if (res.entity.buffer.length <= limit) bb = BufferBuilder(res.entity.buffer)
              else closeWithError()
            }

            case HttpEvent(MessageChunk(body, _)) => if (!closed) {
              if (bb.size + body.length <= limit) bb.append(body)
              else closeWithError()
            }

            case HttpEvent(_: ChunkedMessageEnd) => if (!closed) {
              eventPL(HttpEvent(response.copy(entity = response.entity.map((ct, _) => ct -> bb.toArray))))
              response = null
              bb = null
            }

            case ev => eventPL(ev)
          }

          def closeWithError() {
            val msg = "Aggregated response entity greater than configured limit of " + limit + " bytes"
            commandPL(HttpClient.Close(ProtocolError(msg)))
            closed = true
          }
        }
    }
}