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

package spray.can.rendering

import spray.util._
import spray.http._

private[rendering] object RenderSupport {
  val DefaultStatusLine = "HTTP/1.1 200 OK\r\n".getAsciiBytes
  val StatusLineStart = "HTTP/1.1 ".getAsciiBytes
  val Chunked = "chunked".getAsciiBytes
  val KeepAlive = "Keep-Alive".getAsciiBytes
  val Close = "close".getAsciiBytes

  def CrLf = Rendering.CrLf

  implicit object MessageChunkRenderer extends Renderer[MessageChunk] {
    def render[R <: Rendering](r: R, chunk: MessageChunk): r.type = {
      import chunk._
      r ~~% data.length
      if (!extension.isEmpty) r ~~ ';' ~~ extension
      r ~~ CrLf ~~ data ~~ CrLf
    }
  }

  implicit object ChunkedMessageEndRenderer extends Renderer[ChunkedMessageEnd] {
    implicit val trailerRenderer = Renderer.seqRenderer[Renderable, HttpHeader](CrLf)
    def render[R <: Rendering](r: R, part: ChunkedMessageEnd): r.type = {
      r ~~ '0'
      if (!part.extension.isEmpty) r ~~ ';' ~~ part.extension
      r ~~ CrLf
      if (!part.trailer.isEmpty) r ~~ part.trailer ~~ CrLf
      r ~~ CrLf
    }
  }
}