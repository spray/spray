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

package spray.can.client

import spray.http.HttpHeaders.`User-Agent`
import spray.http.HttpDataRendering
import spray.can.rendering._
import spray.io._
import spray.util._

private[can] object RequestRendering {

  def apply(settings: ClientConnectionSettings): PipelineStage =
    new PipelineStage with RequestRenderingComponent {
      val userAgent = settings.userAgentHeader.toOption.map(`User-Agent`(_))
      val chunklessStreaming = settings.chunklessStreaming

      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          val commandPipeline: CPL = {
            case ctx: RequestPartRenderingContext ⇒
              val rendering = new HttpDataRendering(settings.requestHeaderSizeHint)
              renderRequestPartRenderingContext(rendering, ctx, context.remoteAddress, context.log)
              commandPL(toTcpWriteCommand(rendering.get, ctx.ack))

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline = eventPL
        }
    }
}
