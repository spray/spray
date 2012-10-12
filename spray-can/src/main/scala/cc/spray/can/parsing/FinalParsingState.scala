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

package spray.can.parsing

import spray.can.{StatusLine, RequestLine, MessageLine}
import spray.util.EmptyByteArray
import spray.http._


sealed trait FinalParsingState extends ParsingState

sealed trait HttpMessagePartCompletedState extends FinalParsingState {
  def toHttpMessagePart: HttpMessagePart
}

sealed trait HttpMessageStartCompletedState extends HttpMessagePartCompletedState {
  def toHttpMessagePart: HttpMessageStart
  def messageLine: MessageLine
  def headers: List[HttpHeader]
  def connectionHeader: Option[String]
  def contentType: Option[ContentType]
}

sealed trait HttpMessageEndCompletedState extends HttpMessagePartCompletedState

case class CompleteMessageState(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  contentType: Option[ContentType] = None,
  body: Array[Byte] = EmptyByteArray
) extends HttpMessageStartCompletedState with HttpMessageEndCompletedState {

  def toHttpMessagePart = messageLine match {
    case x: RequestLine => HttpRequest(x.method, x.uri, headers, entity, x.protocol)
    case x: StatusLine => HttpResponse(x.status, entity, headers, x.protocol)
  }

  def entity = if (contentType.isEmpty) HttpEntity(body) else HttpBody(contentType.get, body)
}


case class ChunkedStartState(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  contentType: Option[ContentType] = None
) extends HttpMessageStartCompletedState {

  def toHttpMessagePart = messageLine match {
    case x: RequestLine => ChunkedRequestStart(HttpRequest(x.method, x.uri, headers, entity))
    case x: StatusLine => ChunkedResponseStart(HttpResponse(x.status, entity, headers))
  }

  def entity = if (contentType.isEmpty) EmptyEntity else HttpBody(contentType.get, EmptyByteArray)
}


case class ChunkedChunkState(
  extensions: List[ChunkExtension],
  body: Array[Byte]
) extends HttpMessagePartCompletedState {

  def toHttpMessagePart = MessageChunk(body, extensions)
}


case class ChunkedEndState(
  extensions: List[ChunkExtension],
  trailer: List[HttpHeader]
) extends HttpMessageEndCompletedState {

  def toHttpMessagePart = ChunkedMessageEnd(extensions, trailer)
}


case class Expect100ContinueState(next: ParsingState) extends FinalParsingState

case class ErrorState(status: StatusCode, summary: String, detail: String) extends FinalParsingState {
  def message = if (detail.isEmpty) summary else summary + ": " + detail
}

object ErrorState {
  val Dead = ErrorState(StatusCodes.OK, "", "")
  def apply(summary: String): ErrorState = apply(summary, "")
  def apply(summary: String, detail: String): ErrorState = apply(StatusCodes.BadRequest, summary, detail)
  def apply(status: StatusCode, summary: String): ErrorState = apply(status, summary, "")
  def apply(status: StatusCode): ErrorState = apply(status, status.defaultMessage)
}
