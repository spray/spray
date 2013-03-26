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

package spray.can.rendering

import scala.annotation.tailrec
import akka.util.{ ByteString, ByteStringBuilder }
import spray.can.parsing.isTokenChar
import spray.util._
import spray.http._

private[rendering] object MessageRendering {
  val DefaultStatusLine = "HTTP/1.1 200 OK\r\n".getAsciiBytes
  val CrLf = "\r\n".getAsciiBytes
  val SomeClose = Some("close")

  def put(header: HttpHeader)(implicit bb: ByteStringBuilder): this.type =
    putHeader(header.name, header.value)

  def putHeader(name: String, value: String)(implicit bb: ByteStringBuilder): this.type =
    put(name).put(':').put(' ').put(value).put(CrLf)

  @tailrec
  final def putHeaders(h: List[HttpHeader])(implicit bb: ByteStringBuilder): this.type = h match {
    case Nil          ⇒ this
    case head :: tail ⇒ put(head); putHeaders(tail)
  }

  def putContentTypeHeaderIfRequired(entity: HttpEntity)(implicit bb: ByteStringBuilder): this.type =
    if (!entity.isEmpty) putHeader("Content-Type", entity.asInstanceOf[HttpBody].contentType.value)
    else this

  def renderChunk(chunk: MessageChunk, messageSizeHint: Int): RenderedMessagePart = {
    implicit val bb = newByteStringBuilder(messageSizeHint)
    putChunk(chunk.extensions, chunk.body)
    RenderedMessagePart(bb.result())
  }

  def putChunk(extensions: List[ChunkExtension], body: Array[Byte])(implicit bb: ByteStringBuilder): this.type =
    put(Integer.toHexString(body.length)).put(extensions).put(CrLf).put(body).put(CrLf)

  def renderFinalChunk(chunk: ChunkedMessageEnd, messageSizeHint: Int,
                       requestConnectionHeader: Option[String] = None): RenderedMessagePart = {
    implicit val bb = newByteStringBuilder(messageSizeHint)
    put('0').put(chunk.extensions).put(CrLf).putHeaders(chunk.trailer).put(CrLf)
    RenderedMessagePart(bb.result(), closeConnection = requestConnectionHeader == SomeClose)
  }

  @tailrec
  private def put(extensions: List[ChunkExtension])(implicit bb: ByteStringBuilder): this.type =
    extensions match {
      case Nil ⇒ this
      case ChunkExtension(name, value) :: rest ⇒
        put(';').put(name).put('=')
        if (value.forall(isTokenChar)) put(value) else put('"').put(value).put('"')
        put(rest)
    }

  @tailrec
  final def put(string: String, startIndex: Int = 0)(implicit bb: ByteStringBuilder): this.type =
    if (startIndex < string.length) {
      put(string.charAt(startIndex))
      put(string, startIndex + 1)
    }
    else this

  def put(c: Char)(implicit bb: ByteStringBuilder): this.type = {
    bb.putByte(c.asInstanceOf[Byte])
    this
  }

  def put(bytes: Array[Byte])(implicit bb: ByteStringBuilder): this.type = {
    bb.putBytes(bytes)
    this
  }

  def newByteStringBuilder(sizeHint: Int): ByteStringBuilder = {
    val bb = ByteString.newBuilder
    bb.sizeHint(sizeHint)
    bb
  }
}