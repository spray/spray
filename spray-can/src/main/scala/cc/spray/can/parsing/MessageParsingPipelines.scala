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

package cc.spray.can.parsing

import java.nio.ByteBuffer
import annotation.tailrec
import cc.spray.io._
import cc.spray.can.model.{HttpMessageEndPart, ChunkedMessageEnd, HttpMessage}

abstract class MessageParsingPipelines(settings: ParserSettings,
                                       commandPL: Pipeline[Command],
                                       eventPL: Pipeline[Event]) extends Pipeline[Event] {
  var currentParsingState: ParsingState = _

  def startParser: ParsingState

  def handleExpect100Continue(nextState: ParsingState): ParsingState

  def handleParseError(errorState: ErrorState)

  @tailrec
  final def parse(buffer: ByteBuffer) {
    currentParsingState match {
      case x: IntermediateState =>
        if (buffer.remaining > 0) {
          currentParsingState = x.read(buffer)
          parse(buffer)
        } // else wait for more input

      case x: HttpMessagePartCompletedState =>
        val messagePart = x.toHttpMessagePart
        currentParsingState =
          if (messagePart.isInstanceOf[HttpMessageEndPart]) startParser
          else new ChunkParser(settings)
        eventPL(messagePart) // dispatch
        parse(buffer)

      case Expect100ContinueState(nextState) =>
        currentParsingState = handleExpect100Continue(nextState)
        parse(buffer)

      case ErrorState(_, -1) => // if we already handled the error state we ignore all further input

      case x: ErrorState =>
        handleParseError(x)
        currentParsingState = ErrorState("", -1) // set to "special" ErrorState that ignores all further input
    }
  }

  def apply(event: Event) {
    event match {
      case x: IoPeer.Received => parse(x.buffer)
      case ev => eventPL(ev)
    }
  }

}

