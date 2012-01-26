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

import java.nio.ByteBuffer
import model.{RequestLine, HttpResponse}
import nio._
import rendering.HttpResponseRenderer

object HttpResponseRendering {
  def apply(serverHeader: String)
           (innerPipeline: ByteBuffer ~~> HttpResponseRenderingContext)
           : ByteBuffer ~~> Seq[ByteBuffer] = {

    val renderer = new HttpResponseRenderer(serverHeader)

    ctx => innerPipeline {
      ctx.withPush { renderingContext =>
        import renderingContext._
        val renderedResponse = renderer.renderResponse(requestLine, response, requestConnectionHeader)
        ctx.push(renderedResponse.buffers)
        if (renderedResponse.closeConnection) ctx.close()
      }
    }
  }
}

case class HttpResponseRenderingContext(
  response: HttpResponse,
  requestLine: RequestLine,                 // response rendering is influenced the request protocol, HTTP method
  requestConnectionHeader: Option[String]   // and connection header
)