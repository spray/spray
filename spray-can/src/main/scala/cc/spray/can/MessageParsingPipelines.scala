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

import config.HttpParserConfig
import model.HttpMessage
import java.nio.ByteBuffer
import annotation.tailrec
import parsing._
import cc.spray.io._

abstract class MessageParsingPipelines(parserConfig: HttpParserConfig,
                                           commandPL: Pipeline[Command],
                                           eventPL: Pipeline[Event]) extends Pipelines {
  var currentParsingState = startParser

  def startParser: ParsingState
  def handleParseError(errorState: ErrorState)

  @tailrec
  final def parse(buffer: ByteBuffer) {
    currentParsingState match {
      case x: IntermediateState =>
        currentParsingState = x.read(buffer)
        currentParsingState match {
          case x: IntermediateState => // wait for more input

          case x: HttpMessagePartCompletedState =>
            val messagePart = x.toHttpMessagePart
            eventPL(messagePart) // dispatch
            currentParsingState = messagePart match {
              case _: HttpMessage => startParser
              case _ => new ChunkParser(parserConfig)
            }
            if (buffer.remaining > 0) parse(buffer) // there might be more input in the buffer, so recurse

          case x: ErrorState => handleParseError(x)
        }

      case x: ErrorState => // if we are already in the errorstate we ignore all further input
    }
  }

  def commandPipeline(command: Command) { commandPL(command) }

  def eventPipeline(event: Event) {
    event match {
      case x: IoWorker.Received => parse(x.buffer)
      case ev => eventPL(ev)
    }
  }

}

