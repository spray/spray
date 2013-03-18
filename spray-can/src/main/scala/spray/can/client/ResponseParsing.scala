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

import java.nio.ByteBuffer
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import spray.can.rendering.HttpRequestPartRenderingContext
import spray.util.EmptyByteArray
import spray.util.ConnectionCloseReasons._
import spray.can.parsing._
import spray.http._
import spray.io._
import akka.io.Tcp
import akka.util.{ByteString, ByteIterator}
import spray.can.Http


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
              case x: IntermediateState =>
                if (data.hasNext) {
                  currentParsingState = x.read(data)
                  parse(data)
                } // else wait for more input

              case x: HttpMessagePartCompletedState => x.toHttpMessagePart(log) match {
                case part: HttpMessageEnd =>
                  eventPL(Http.MessageEvent(part))
                  openRequestMethods = openRequestMethods.tail
                  if (openRequestMethods.isEmpty) {
                    currentParsingState = UnmatchedResponseErrorState
                    if (data.hasNext) parse(data) // trigger error if buffer is not empty
                  } else {
                    currentParsingState = startParser
                    parse(data)
                  }
                case part =>
                  eventPL(Http.MessageEvent(part))
                  currentParsingState = new ChunkParser(settings)
                  parse(data)
              }

              case _: Expect100ContinueState =>
                currentParsingState = ErrorState("'Expect: 100-continue' is not allowed in HTTP responses")
                parse(data) // trigger error

              case ErrorState.Dead => // if we already handled the error state we ignore all further input

              case x: ErrorState =>
                log.warning("Received illegal response: {}", x.message)
                commandPL(Http.Close)
                currentParsingState = ErrorState.Dead // set to "special" ErrorState that ignores all further input
            }
          }

          val commandPipeline: CPL = {
            case x: HttpRequestPartRenderingContext =>
              def register(req: HttpRequest) {
                openRequestMethods = openRequestMethods enqueue req.method
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
            case Tcp.Received(data) => parse(data.iterator)

            case ev@ Http.PeerClosed =>
              currentParsingState match {
                case x: ToCloseBodyParser =>
                  currentParsingState = x.complete
                  parse(ByteString.empty.iterator)
                case _ =>
              }
              eventPL(ev)

            case ev => eventPL(ev)
          }
        }
    }
  }
}