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
package rendering

import java.lang.{StringBuilder => JStringBuilder}
import java.nio.ByteBuffer
import model.HttpRequest

class HttpRequestRenderer(userAgentHeader: String) {
  import HttpMessageRendering._

  def renderRequest(request: HttpRequest, host: String, port: Int): List[ByteBuffer] = {
    val sb = prepareRequestStart(request, host, port)
    val bodyBufs = if (request.body.length > 0) {
      appendHeader("Content-Length", request.body.length.toString, sb)
      appendLine(sb)
      ByteBuffer.wrap(request.body) :: Nil
    } else {
      appendLine(sb)
      Nil
    }
    encode(sb) :: bodyBufs
  }

  def renderChunkedRequestStart(request: HttpRequest, host: String, port: Int): List[ByteBuffer] = {
    val sb = prepareRequestStart(request, host, port)
    appendHeader("Transfer-Encoding", "chunked", sb)
    appendLine(sb)
    encode(sb) :: { if (request.body.length > 0) prepareChunk(Nil, request.body) else Nil }
  }

  private def prepareRequestStart(request: HttpRequest, host: String, port: Int) = {
    import request._
    val sb = new JStringBuilder(256)
    appendLine(sb.append(method.name).append(' ').append(uri).append(' ').append(protocol.name))
    appendHeaders(headers, sb)
    appendHeader("Host", if (port == 80) host else host + ':' + port, sb)
    if (!userAgentHeader.isEmpty) appendHeader("User-Agent", userAgentHeader, sb)
    sb
  }
}