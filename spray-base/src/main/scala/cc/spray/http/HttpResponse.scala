/*
 * Copyright (C) 2011-2012 spray.cc
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package cc.spray.http
import HttpProtocols._
import java.nio.charset.Charset

/**
 * Sprays immutable model of an HTTP response.
 */
case class HttpResponse(
  status: StatusCode,
  headers: List[HttpHeader],
  content: Option[HttpContent],
  protocol: HttpProtocol
) extends HttpMessage[HttpResponse] {

  def withHeaders(headers: List[HttpHeader]) = copy(headers = headers)

  def withContent(content: Option[HttpContent]) = copy(content = content)

  def withHeadersAndContent(headers: List[HttpHeader], content: Option[HttpContent]) =
    copy(headers = headers, content = content)
}

object HttpResponse {
  def apply(status: StatusCode): HttpResponse = apply(status, Nil, None, `HTTP/1.1`)
  def apply(status: StatusCode, headers: List[HttpHeader]): HttpResponse = apply(status, headers, None, `HTTP/1.1`)
  def apply(status: StatusCode, content: HttpContent): HttpResponse = apply(status, Nil, content)
  def apply(status: StatusCode, headers: List[HttpHeader], content: HttpContent): HttpResponse = {
    apply(status, headers, Some(content), `HTTP/1.1`)
  }
  def apply(status: StatusCode, content: String): HttpResponse = apply(status, Nil, content)
  def apply(status: StatusCode, headers: List[HttpHeader], content: String): HttpResponse = {
    apply(status, headers, Some(HttpContent(content)), `HTTP/1.1`)
  }
}

/**
 * Instance of this class represent the individual chunks of a chunked HTTP message (request or response).
 */
case class MessageChunk(body: Array[Byte], extensions: List[ChunkExtension]) {
  require(body.length > 0, "MessageChunk must not have empty body")
  def bodyAsString: String = bodyAsString(HttpCharsets.`ISO-8859-1`.nioCharset)
  def bodyAsString(charset: HttpCharset): String = bodyAsString(charset.nioCharset)
  def bodyAsString(charset: Charset): String = if (body.isEmpty) "" else new String(body, charset)
  def bodyAsString(charset: String): String = if (body.isEmpty) "" else new String(body, charset)
}

object MessageChunk {
  import HttpCharsets._
  def apply(body: String): MessageChunk =
    apply(body, Nil)
  def apply(body: String, charset: HttpCharset): MessageChunk =
    apply(body, charset, Nil)
  def apply(body: String, extensions: List[ChunkExtension]): MessageChunk =
    apply(body, `ISO-8859-1`, extensions)
  def apply(body: String, charset: HttpCharset, extensions: List[ChunkExtension]): MessageChunk =
    apply(body.getBytes(charset.nioCharset), extensions)
  def apply(body: Array[Byte]): MessageChunk =
    apply(body, Nil)
}

case class ChunkExtension(name: String, value: String)