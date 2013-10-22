/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import scala.util.control.NonFatal
import scala.collection.immutable.Queue
import akka.io.Tcp
import akka.util.ByteString
import spray.can.rendering.RequestPartRenderingContext
import spray.can.Http
import spray.can.parsing._
import spray.util._
import spray.http._
import spray.io._

private object ResponseParsing {

  def apply(settings: ParserSettings): PipelineStage = {
    val rootParser = new HttpResponsePartParser(settings)()
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          val responseParser = rootParser.copyWith { errorInfo ⇒
            if (settings.illegalHeaderWarnings)
              log.warning(errorInfo.withSummaryPrepended("Illegal response header").formatPretty)
          }
          var parser: Parser = responseParser
          var openRequestMethods = Queue.empty[HttpMethod]

          @tailrec def handleParsingResult(result: Result): Unit =
            result match {
              case Result.Emit(part, closeAfterResponseCompletion, continue) ⇒
                eventPL(Http.MessageEvent(part))
                if (part.isInstanceOf[HttpMessageEnd]) {
                  if (closeAfterResponseCompletion) {
                    commandPL(Http.Close)
                    parser = Result.IgnoreAllFurtherInput
                  } else {
                    openRequestMethods = openRequestMethods.tail
                    responseParser.setRequestMethodForNextResponse {
                      if (openRequestMethods.isEmpty) HttpResponsePartParser.NoMethod else openRequestMethods.head
                    }
                    handleParsingResult(continue())
                  }
                } else handleParsingResult(continue())

              case Result.NeedMoreData(next)    ⇒ parser = next // wait for the next packet

              case Result.ParsingError(_, info) ⇒ handleError(info)

              case Result.IgnoreAllFurtherInput ⇒

              case Result.Expect100Continue(_) ⇒
                handleError(ErrorInfo("'Expect: 100-continue' is not allowed in HTTP responses"))
            }

          val commandPipeline: CPL = {
            case x @ RequestPartRenderingContext(reqStart: HttpMessageStart, _, _) ⇒
              val req = reqStart.message.asInstanceOf[HttpRequest]
              if (openRequestMethods.isEmpty) responseParser.setRequestMethodForNextResponse(req.method)
              openRequestMethods = openRequestMethods enqueue req.method
              commandPL(x)

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case Tcp.Received(data: ByteString) ⇒ processReceivedData(data)

            case ev @ Http.PeerClosed ⇒
              processReceivedData(ByteString.empty)
              eventPL(ev)

            case ev ⇒ eventPL(ev)
          }

          def processReceivedData(data: ByteString): Unit =
            try handleParsingResult(parser(data))
            catch {
              case NonFatal(e) ⇒
                handleError(ErrorInfo(
                  "Invalid response",
                  e.getMessage.nullAsEmpty))
            }

          def handleError(info: ErrorInfo): Unit = {
            log.warning("Received illegal response: {}", info.formatPretty)
            commandPL(Http.Close)
            parser = Result.IgnoreAllFurtherInput
          }
        }
    }
  }
}
