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

package spray.can.server

import akka.event.LoggingAdapter
import java.nio.ByteBuffer
import annotation.tailrec
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.can.HttpEvent
import spray.util.ProtocolError
import spray.can.parsing._
import spray.io._
import spray.http._


object RequestParsing {

  lazy val continue = "HTTP/1.1 100 Continue\r\n\r\n".getBytes("ASCII")

  def apply(settings: ParserSettings, verboseErrorMessages: Boolean, log: LoggingAdapter): PipelineStage =
    new PipelineStage {
      val startParser = new EmptyRequestParser(settings)

      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var currentParsingState: ParsingState = startParser

          @tailrec
          final def parse(buffer: ByteBuffer) {
            currentParsingState match {
              case x: IntermediateState =>
                if (buffer.remaining > 0) {
                  currentParsingState = x.read(buffer)
                  parse(buffer)
                } // else wait for more input

              case x: HttpMessageStartCompletedState =>
                eventPL(HttpMessageStartEvent(x.toHttpMessagePart, x.connectionHeader))
                currentParsingState =
                  if (x.isInstanceOf[HttpMessageEndCompletedState]) startParser
                  else new ChunkParser(settings)
                parse(buffer)

              case x: HttpMessagePartCompletedState =>
                eventPL(HttpEvent(x.toHttpMessagePart))
                currentParsingState =
                  if (x.isInstanceOf[HttpMessageEndCompletedState]) startParser
                  else new ChunkParser(settings)
                parse(buffer)

              case Expect100ContinueState(nextState) =>
                commandPL(IOPeer.Send(ByteBuffer.wrap(continue)))
                currentParsingState = nextState
                parse(buffer)

              case ErrorState.Dead => // if we already handled the error state we ignore all further input

              case x: ErrorState =>
                handleParseError(x)
                currentParsingState = ErrorState.Dead // set to "special" ErrorState that ignores all further input
            }
          }

          def handleParseError(state: ErrorState) {
            log.warning("Illegal request, responding with status {} and '{}'", state.status, state.message)
            val msg = if (verboseErrorMessages) state.message else state.summary
            val response = HttpResponse(state.status, msg)

            // In case of a request parsing error we probably stopped reading the request somewhere in between,
            // where we cannot simply resume. Resetting to a known state is not easy either,
            // so we need to close the connection to do so.
            commandPL(HttpResponsePartRenderingContext(response))
            commandPL(HttpServer.Close(ProtocolError(state.message)))
          }

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case x: IOPeer.Received => parse(x.buffer)
            case ev => eventPL(ev)
          }
        }
    }

  ////////////// EVENTS //////////////

  case class HttpMessageStartEvent(messagePart: HttpMessageStart, connectionHeader: Option[String]) extends Event
}