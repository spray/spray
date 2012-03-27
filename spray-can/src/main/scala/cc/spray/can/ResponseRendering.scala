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

import rendering.{HttpResponsePartRenderingContext, ResponseRenderer}
import io._

object ResponseRendering {

  def apply(serverHeader: String, chunklessStreaming: Boolean, responseSizeHint: Int): CommandPipelineStage = {
    new CommandPipelineStage {
      val renderer = new ResponseRenderer(serverHeader, chunklessStreaming, responseSizeHint)

      def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
        case ctx: HttpResponsePartRenderingContext =>
          val rendered = renderer.render(ctx)
          val buffers = rendered.buffers
          if (!buffers.isEmpty)
            commandPL(IoPeer.Send(buffers))
          if (rendered.closeConnection)
            commandPL(IoPeer.Close(CleanClose))

        case cmd => commandPL(cmd)
      }
    }
  }
}