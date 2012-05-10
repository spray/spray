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
package parsing

import model._

sealed trait FinalParsingState extends ParsingState

sealed trait HttpMessagePartCompletedState extends FinalParsingState {
  def toHttpMessagePart: HttpMessagePart
}

sealed trait HttpMessageCompletedState extends HttpMessagePartCompletedState

case class CompleteMessageState(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None,
  body: Array[Byte] = cc.spray.util.EmptyByteArray
) extends HttpMessageCompletedState {

  def toHttpMessagePart = messageLine match {
    case x: RequestLine => HttpRequest(x.method, x.uri, headers, body, x.protocol)
    case x: StatusLine => HttpResponse(x.status, headers, body, x.protocol)
  }
}


case class ChunkedStartState(
  messageLine: MessageLine,
  headers: List[HttpHeader] = Nil,
  connectionHeader: Option[String] = None
) extends HttpMessagePartCompletedState {

  def toHttpMessagePart = messageLine match {
    case x: RequestLine => ChunkedRequestStart(HttpRequest(x.method, x.uri, headers))
    case x: StatusLine => ChunkedResponseStart(HttpResponse(x.status, headers))
  }
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
) extends HttpMessageCompletedState {

  def toHttpMessagePart = ChunkedMessageEnd(extensions, trailer)
}


case class Expect100ContinueState(next: ParsingState) extends FinalParsingState
case class ErrorState(message: String, status: Int = 400) extends FinalParsingState
