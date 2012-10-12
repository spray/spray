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

package spray.can.client

import akka.event.LoggingAdapter
import collection.mutable
import java.nio.ByteBuffer
import annotation.tailrec
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.can.HttpEvent
import spray.util.EmptyByteArray
import spray.can.parsing._
import spray.http._
import spray.io._
import spray.util._


object ResponseParsing {

  private val UnmatchedResponseErrorState = ErrorState("Response to non-existent request")

  def apply(settings: ParserSettings, log: LoggingAdapter): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var currentParsingState: ParsingState = UnmatchedResponseErrorState
          val openRequestMethods = mutable.Queue.empty[HttpMethod]

          def startParser = new EmptyResponseParser(settings, openRequestMethods.head == HttpMethods.HEAD)

          @tailrec
          final def parse(buffer: ByteBuffer) {
            currentParsingState match {
              case x: IntermediateState =>
                if (buffer.remaining > 0) {
                  currentParsingState = x.read(buffer)
                  parse(buffer)
                } // else wait for more input

              case x: HttpMessagePartCompletedState => x.toHttpMessagePart match {
                case part: HttpMessageEnd =>
                  eventPL(HttpEvent(part))
                  openRequestMethods.dequeue()
                  if (openRequestMethods.isEmpty) {
                    currentParsingState = UnmatchedResponseErrorState
                    if (buffer.remaining > 0) parse(buffer) // trigger error if buffer is not empty
                  } else {
                    currentParsingState = startParser
                    parse(buffer)
                  }
                case part =>
                  eventPL(HttpEvent(part))
                  currentParsingState = new ChunkParser(settings)
                  parse(buffer)
              }

              case _: Expect100ContinueState =>
                currentParsingState = ErrorState("'Expect: 100-continue' is not allowed in HTTP responses")
                parse(buffer) // trigger error

              case ErrorState.Dead => // if we already handled the error state we ignore all further input

              case x: ErrorState =>
                log.warning("Received illegal response: {}", x.message)
                commandPL(IOPeer.Close(ProtocolError(x.message)))
                currentParsingState = ErrorState.Dead // set to "special" ErrorState that ignores all further input
            }
          }

          val commandPipeline: CPL = {
            case x: HttpRequestPartRenderingContext =>
              def register(req: HttpRequest) {
                openRequestMethods.enqueue(req.method)
                if (currentParsingState eq UnmatchedResponseErrorState) currentParsingState = startParser
              }
              x.requestPart match {
                case x: HttpRequest => register(x)
                case x: ChunkedRequestStart => register(x.request)
                case _ =>
              }
              commandPL(x)

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case x: IOPeer.Received => parse(x.buffer)

            case ev@IOPeer.Closed(_, PeerClosed) =>
              currentParsingState match {
                case x: ToCloseBodyParser =>
                  currentParsingState = x.complete
                  parse(ByteBuffer.wrap(EmptyByteArray))
                case _ =>
              }
              eventPL(ev)

            case ev => eventPL(ev)
          }
        }
    }
}