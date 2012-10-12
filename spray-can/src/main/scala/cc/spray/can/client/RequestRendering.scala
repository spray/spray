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

import spray.can.rendering.{HttpRequestPartRenderingContext, RequestRenderer}
import spray.io._


object RequestRendering {

  def apply(settings: ClientSettings): PipelineStage =
    new PipelineStage {
      val renderer = new RequestRenderer(settings.UserAgentHeader, settings.RequestSizeHint.toInt)

      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val commandPipeline: CPL = {
            case ctx: HttpRequestPartRenderingContext =>
              val rendered = renderer.render(ctx)
              commandPL(IOPeer.Send(rendered.buffers, ctx.sentAck))

            case cmd => commandPL(cmd)
          }

          val eventPipeline = eventPL
        }
    }
}