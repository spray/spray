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

import rendering.{HttpRequestPartRenderingContext, RequestRenderer}
import akka.actor.ActorContext
import cc.spray.io._

object RequestRendering {

  def apply(userAgentHeader: String) = new CommandPipelineStage {
    val renderer = new RequestRenderer(userAgentHeader)

    def build(context: ActorContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = {
      case ctx: HttpRequestPartRenderingContext =>
        val rendered = renderer.render(ctx)
        commandPL(IoPeer.Send(rendered.buffers))

      case cmd => commandPL(cmd)
    }
  }
}