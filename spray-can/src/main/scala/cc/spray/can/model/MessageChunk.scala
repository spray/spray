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

package cc.spray.can.model

import java.nio.charset.Charset

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(body: Array[Byte], extensions: List[ChunkExtension]) {
  require(body.length > 0, "MessageChunk must not have empty body")
  def bodyAsString: String = bodyAsString("ISO-88591-1")
  def bodyAsString(charset: Charset): String = if (body.isEmpty) "" else new String(body, charset)
  def bodyAsString(charset: String): String = if (body.isEmpty) "" else new String(body, charset)
}

object MessageChunk {
  def apply(body: String): MessageChunk =
    apply(body, Nil)
  def apply(body: String, charset: String): MessageChunk =
    apply(body, charset, Nil)
  def apply(body: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body, "ISO-8859-1", extensions)
  def apply(body: String, charset: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body.getBytes(charset), extensions)
  def apply(body: Array[Byte]): MessageChunk =
    apply(body, Nil)
}

case class ChunkExtension(name: String, value: String)