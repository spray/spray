/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.can.rendering

import cc.spray.can.model._
import cc.spray.can.parsing.isTokenChar
import cc.spray.util._
import cc.spray.io.BufferBuilder
import annotation.tailrec

private[rendering] trait MessageRendering {
  import MessageRendering._

  protected def appendHeader(name: String, value: String, bb: BufferBuilder) =
    bb.append(name).append(':').append(' ').append(value).append(CrLf)

  @tailrec
  protected final def appendHeaders(httpHeaders: List[HttpHeader], bb: BufferBuilder,
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
      appendHeader(header.name, header.value, bb)
      appendHeaders(httpHeaders.tail, bb, newConnectionHeaderValue)
    }
  }

  protected def renderChunk(chunk: MessageChunk, messageSizeHint: Int): RenderedMessagePart = {
    val bb = BufferBuilder(messageSizeHint)
    renderChunk(chunk.extensions, chunk.body, bb)
    RenderedMessagePart(bb.toByteBuffer :: Nil)
  }

  protected def renderChunk(extensions: List[ChunkExtension], body: Array[Byte], bb: BufferBuilder) = {
    bb.append(Integer.toHexString(body.length))
    appendChunkExtensions(extensions, bb).append(CrLf).append(body).append(CrLf)
  }

  protected def renderFinalChunk(chunk: ChunkedMessageEnd, messageSizeHint: Int,
                                 requestConnectionHeader: Option[String] = None): RenderedMessagePart = {
    val bb = BufferBuilder(messageSizeHint).append('0')
    appendChunkExtensions(chunk.extensions, bb).append(CrLf)
    appendHeaders(chunk.trailer, bb)
    bb.append(CrLf)
    RenderedMessagePart(bb.toByteBuffer :: Nil, closeConnection = requestConnectionHeader == SomeClose)
  }

  @tailrec
  private def appendChunkExtensions(extensions: List[ChunkExtension], bb: BufferBuilder): BufferBuilder = {
    extensions match {
      case Nil => bb
      case ChunkExtension(name, value) :: rest => appendChunkExtensions(rest, {
        bb.append(';').append(name).append('=')
        if (value.forall(isTokenChar)) bb.append(value) else bb.append('"').append(value).append('"')
      })
    }
  }
}

private[rendering] object MessageRendering {
  val DefaultStatusLine = "HTTP/1.1 200 OK\r\n".getAsciiBytes
  val CrLf = "\r\n".getAsciiBytes
  val SomeClose = Some("close")
}