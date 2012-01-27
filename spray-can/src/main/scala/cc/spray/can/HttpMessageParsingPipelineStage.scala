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
import model.{HttpMessagePart, HttpMessage}
import nio.Pipelines
import java.nio.ByteBuffer
import annotation.tailrec
import parsing._

abstract class HttpMessageParsingPipelineStage {
  var currentParsingState = startParser

  def startParser: ParsingState
  def parserConfig: HttpParserConfig
  def pipelines: Pipelines
  def handleParseError(errorState: ErrorState)

  @tailrec
  final def apply(buffer: ByteBuffer) {
    currentParsingState match {
      case x: IntermediateState =>
        currentParsingState = x.read(buffer)
        currentParsingState match {
          case x: IntermediateState => // wait for more input

          case x: HttpMessagePartCompletedState =>
            dispatch(x.toHttpMessagePart)
            if (buffer.remaining > 0) apply(buffer) // there might be more input in the buffer, so recurse

          case x: ErrorState => handleParseError(x)
        }
      case x: ErrorState => // if we are already in the errorstate we ignore all further input
    }
  }

  def dispatch(messagePart: HttpMessagePart) {
    pipelines.upstream(messagePart)
    currentParsingState = messagePart match {
      case _: HttpMessage => startParser
      case _ => new ChunkParser(parserConfig)
    }
  }

}

