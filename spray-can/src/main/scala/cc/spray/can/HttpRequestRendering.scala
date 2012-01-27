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

import nio._
import rendering.{HttpRequestPartRenderingContext, HttpRequestRenderer}

object HttpRequestRendering {

  def apply(userAgentHeader: String)(pipelines: Pipelines) = {
    val renderer = new HttpRequestRenderer(userAgentHeader)
    pipelines.withDownstream {
      case ctx: HttpRequestPartRenderingContext =>
        val rendered = renderer.render(ctx)
        pipelines.downstream {
          Send(pipelines.handle, rendered.buffers)
        }

      case event => pipelines.downstream(event)
    }
  }
}