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

package spray.can.client

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import akka.io.Tcp
import akka.util.{ ByteString, ByteIterator }
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.can.{ StatusLine, Http }
import spray.can.parsing._
import spray.http._
import spray.io._
import spray.http.parser.HttpParser

object ResponseParsing {

  private val UnmatchedResponseErrorState = ErrorState("Response to non-existent request")

  def apply(settings: ParserSettings): PipelineStage = {
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          var currentParsingState: ParsingState = UnmatchedResponseErrorState
          var openRequestMethods = Queue.empty[HttpMethod]

          def startParser = new EmptyResponseParser(settings, openRequestMethods.head == HttpMethods.HEAD)

          @tailrec
          final def parse(data: ByteIterator) {
            currentParsingState match {
              case x: IntermediateState ⇒
                if (data.hasNext) {
                  currentParsingState = x.read(data)
                  parse(data)
                } // else wait for more input

              case x @ CompleteMessageState(StatusLine(proto, status, _, _), headers, _, _, _) ⇒
                eventPL(Http.MessageEvent(HttpResponse(status, x.entity, parseHeaders(headers), proto)))
                openRequestMethods = openRequestMethods.tail
                if (openRequestMethods.isEmpty) {
                  currentParsingState = UnmatchedResponseErrorState
                  if (data.hasNext) parse(data) // trigger error if buffer is not empty
                } else {
                  currentParsingState = startParser
                  parse(data)
                }

              case x @ ChunkedStartState(StatusLine(proto, status, _, _), headers, _, _) ⇒
                eventPL(Http.MessageEvent(ChunkedResponseStart(HttpResponse(status, x.entity, parseHeaders(headers), proto))))
                currentParsingState = new ChunkParser(settings)
                parse(data)

              case ChunkedChunkState(extensions, body) ⇒
                eventPL(Http.MessageEvent(MessageChunk(body, extensions)))
                currentParsingState = new ChunkParser(settings)
                parse(data)

              case ChunkedEndState(extensions, trailer) ⇒
                eventPL(Http.MessageEvent(ChunkedMessageEnd(extensions, trailer)))
                currentParsingState = startParser
                openRequestMethods = openRequestMethods.tail
                if (openRequestMethods.isEmpty) {
                  currentParsingState = UnmatchedResponseErrorState
                  if (data.hasNext) parse(data) // trigger error if buffer is not empty
                } else {
                  currentParsingState = startParser
                  parse(data)
                }

              case _: Expect100ContinueState ⇒
                currentParsingState = ErrorState("'Expect: 100-continue' is not allowed in HTTP responses")
                parse(data) // trigger error

              case ErrorState.Dead ⇒ // if we already handled the error state we ignore all further input

              case x: ErrorState ⇒
                log.warning("Received illegal response: {}", x.message)
                commandPL(Http.Close)
                currentParsingState = ErrorState.Dead // set to "special" ErrorState that ignores all further input
            }
          }

          def parseHeaders(headers: List[HttpHeader]) = {
            val (errors, parsed) = HttpParser.parseHeaders(headers)
            if (!errors.isEmpty) errors.foreach(e ⇒ log.warning(e.formatPretty))
            parsed
          }

          val commandPipeline: CPL = {
            case x: HttpRequestPartRenderingContext ⇒
              def register(req: HttpRequest) {
                openRequestMethods = openRequestMethods enqueue req.method
                if (currentParsingState eq UnmatchedResponseErrorState) currentParsingState = startParser
              }
              x.requestPart match {
                case x: HttpRequest         ⇒ register(x)
                case x: ChunkedRequestStart ⇒ register(x.request)
                case _                      ⇒
              }
              commandPL(x)

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case Tcp.Received(data) ⇒ parse(data.iterator)

            case ev @ Http.PeerClosed ⇒
              currentParsingState match {
                case x: ToCloseBodyParser ⇒
                  currentParsingState = x.complete
                  parse(ByteString.empty.iterator)
                case _ ⇒
              }
              eventPL(ev)

            case ev ⇒ eventPL(ev)
          }
        }
    }
  }
}