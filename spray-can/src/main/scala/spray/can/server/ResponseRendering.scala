/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import spray.can.Http
import spray.http.HttpDataRendering
import spray.can.rendering._
import spray.io._

private object ResponseRendering {

  def apply(settings: ServerSettings): PipelineStage =
    new PipelineStage with ResponseRenderingComponent {
      def serverHeaderValue: String = settings.serverHeader
      def chunklessStreaming: Boolean = settings.chunklessStreaming

      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val commandPipeline: CPL = {
            case ctx: ResponsePartRenderingContext ⇒
              val rendering = new HttpDataRendering(settings.responseHeaderSizeHint)
              val close = renderResponsePartRenderingContext(rendering, ctx, context.log)
              commandPL(toTcpWriteCommand(rendering.get, ctx.ack))
              if (close) commandPL(Http.ConfirmedClose)

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline = eventPL
        }
    }
}