/*
 * Copyright (C) 2011-2013 spray.io
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

import akka.io.Tcp
import spray.can.rendering.{ ByteStringRendering, ResponseRenderingComponent, ResponsePartRenderingContext }
import spray.io._
import spray.can.Http

object ResponseRendering {

  def apply(settings: ServerSettings): PipelineStage =
    new PipelineStage with ResponseRenderingComponent {
      def serverHeaderValue: String = settings.serverHeader
      def chunklessStreaming: Boolean = settings.chunklessStreaming

      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val commandPipeline: CPL = {
            case ctx: ResponsePartRenderingContext ⇒
              val rendering = new ByteStringRendering(settings.responseSizeHint)
              val close = renderResponsePartRenderingContext(rendering, ctx)
              val data = rendering.get
              if (!data.isEmpty) commandPL(Tcp.Write(data, ctx.ack))
              if (close) commandPL(Http.Close)

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline = eventPL
        }
    }
}