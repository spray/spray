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

import cc.spray.io._
import cc.spray.can.model._
import cc.spray.can.rendering.HttpResponsePartRenderingContext


object RequestChunkAggregation {

  def apply(limit: Int): EventPipelineStage = new EventPipelineStage {
    
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): EPL = new EPL {
      var request: HttpRequest = _
      var bb: BufferBuilder = _
      var closed = false

      def apply(event: Event) {
        event match {
          case ChunkedRequestStart(req) => if (!closed) {
            request = req
            if (req.body.length <= limit) bb = BufferBuilder(req.body)
            else closeWithError()
          }

          case MessageChunk(body, _) => if (!closed) {
            if (bb.size + body.length <= limit) bb.append(body)
            else closeWithError()
          }

          case _: ChunkedMessageEnd => if (!closed) {
            eventPL(request.copy(body = bb.toArray))
            request = null
            bb = null
          }

          case ev => eventPL(ev)
        }
      }

      def closeWithError() {
        val msg = "Aggregated request entity greater than configured limit of " + limit + " bytes"
        commandPL(HttpResponsePartRenderingContext(HttpResponse(413).withBody(msg)))
        commandPL(HttpServer.Close(ProtocolError(msg)))
        closed = true
      }
    }
  }
}