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
import akka.util.CompactByteString
import spray.can.rendering.RequestPartRenderingContext
import spray.can.Http
import spray.can.parsing._
import spray.http._
import spray.io._

object ResponseParsing {

  def apply(settings: ParserSettings): PipelineStage = {
    val rootParser = new HttpResponsePartParser(settings)()
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          val parser = rootParser.copyWith { errorInfo ⇒
            if (settings.illegalHeaderWarnings)
              log.warning(errorInfo.withSummaryPrepended("Illegal response header").formatPretty)
          }
          var openRequestMethods = Queue.empty[HttpMethod]

          @tailrec def parse(data: CompactByteString): Unit =
            parser.parse(data) match {
              case Result.Ok(part, remainingData, closeAfterResponseCompletion) ⇒
                eventPL(Http.MessageEvent(part))
                if (part.isInstanceOf[HttpMessageEnd]) {
                  openRequestMethods = openRequestMethods.tail
                  if (closeAfterResponseCompletion) commandPL(Http.Close)
                  if (openRequestMethods.nonEmpty) parser.startResponse(openRequestMethods.head)
                }
                if (!remainingData.isEmpty) parse(remainingData)

              case Result.NeedMoreData ⇒ // just wait for the next packet

              case Result.ParsingError(_, info) ⇒
                log.warning("Received illegal response: {}", info.formatPretty)
                commandPL(Http.Close)

              case _: Result.Expect100Continue ⇒ throw new IllegalStateException
            }

          val commandPipeline: CPL = {
            case x: RequestPartRenderingContext ⇒
              def register(req: HttpRequest): Unit = {
                if (openRequestMethods.isEmpty) parser.startResponse(req.method)
                openRequestMethods = openRequestMethods enqueue req.method
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
            case Tcp.Received(data: CompactByteString) ⇒ parse(data)

            case ev @ Http.PeerClosed if openRequestMethods.nonEmpty ⇒
              parse(CompactByteString.empty)
              eventPL(ev)

            case ev ⇒ eventPL(ev)
          }
        }
    }
  }
}