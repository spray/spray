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
import annotation.tailrec
import java.nio.ByteBuffer
import model.{ChunkedMessageEnd, MessageChunk, ChunkExtension, HttpHeader}

private[rendering] trait MessageRendering {
  private val CrLf = "\r\n".getBytes("ASCII")

  protected def appendHeader(name: String, value: String, sb: JStringBuilder) =
    appendLine(sb.append(name).append(':').append(' ').append(value))

  @tailrec
  protected final def appendHeaders(httpHeaders: List[HttpHeader], sb: JStringBuilder,
                    connectionHeaderValue: Option[String] = None): Option[String] = {
    if (httpHeaders.isEmpty) {
      connectionHeaderValue
    } else {
      val header = httpHeaders.head
      val newConnectionHeaderValue = {
        if (connectionHeaderValue.isEmpty)
          if (header.name == "Connection") Some(header.value) else None
        else connectionHeaderValue
      }
      appendHeader(header.name, header.value, sb)
      appendHeaders(httpHeaders.tail, sb, newConnectionHeaderValue)
    }
  }

  protected def appendLine(sb: JStringBuilder) = sb.append('\r').append('\n')

  protected def encode(sb: JStringBuilder): ByteBuffer = {
    val chars = new Array[Char](sb.length)
    sb.getChars(0, sb.length, chars, 0)
    val buf = ByteBuffer.allocate(sb.length)
    var i = 0
    while (i < chars.length) {
      buf.put(chars(i).asInstanceOf[Byte])
      i += 1
    }
    buf.flip()
    buf
  }

  protected def renderChunk(chunk: MessageChunk): RenderedMessagePart =
    RenderedMessagePart(renderChunk(chunk.extensions, chunk.body))

  protected def renderChunk(extensions: List[ChunkExtension], body: Array[Byte]): List[ByteBuffer] = {
    val sb = new JStringBuilder(16)
    sb.append(Integer.toHexString(body.length))
    appendLine(appendChunkExtensions(extensions, sb))
    encode(sb) :: ByteBuffer.wrap(body) :: ByteBuffer.wrap(CrLf) :: Nil
  }

  protected def renderFinalChunk(chunk: ChunkedMessageEnd, requestConnectionHeader: Option[String] = None) = {
    val sb = new JStringBuilder(16)
    appendLine(appendChunkExtensions(chunk.extensions, sb.append("0")))
    appendHeaders(chunk.trailer, sb)
    appendLine(sb)
    RenderedMessagePart(encode(sb) :: Nil, requestConnectionHeader.get == "close")
  }

  @tailrec
  private def appendChunkExtensions(extensions: List[ChunkExtension], sb: JStringBuilder): JStringBuilder = {
    extensions match {
      case Nil => sb
      case ChunkExtension(name, value) :: rest => appendChunkExtensions(rest, {
        sb.append(';').append(name).append('=')
        if (value.forall(util.isTokenChar)) sb.append(value) else sb.append('"').append(value).append('"')
      })
    }
  }
}