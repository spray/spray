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

package spray.can.server

import scala.annotation.tailrec
import scala.util.control.NonFatal
import akka.io.Tcp
import akka.util.{ CompactByteString, ByteString }
import spray.can.rendering.ResponsePartRenderingContext
import spray.can.Http
import spray.can.parsing._
import spray.http._
import spray.util._
import spray.io._

private[can] object RequestParsing {

  private val Status100ContinueResponse = Tcp.Write(ByteString("HTTP/1.1 100 Continue\r\n\r\n"))

  def apply(settings: ServerSettings): RawPipelineStage[SslTlsContext] = {
    val rootParser = new HttpRequestPartParser(settings.parserSettings, settings.rawRequestUriHeader)()
    new RawPipelineStage[SslTlsContext] {
      def apply(context: SslTlsContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          val https = settings.sslEncryption && context.sslEngine.isDefined
          var parser: Parser =
            rootParser.copyWith { errorInfo ⇒
              if (settings.parserSettings.illegalHeaderWarnings)
                log.warning(errorInfo.withSummaryPrepended("Illegal request header").formatPretty)
            }

          def normalize(req: HttpRequest) = req.withEffectiveUri(https, settings.defaultHostHeader)

          @tailrec def handleParsingResult(result: Result): Unit =
            result match {
              case Result.NeedMoreData(next) ⇒ parser = next // wait for the next packet

              case Result.Emit(part, closeAfterResponseCompletion, continue) ⇒
                val event = part match {
                  case x: HttpRequest         ⇒ HttpMessageStartEvent(normalize(x), closeAfterResponseCompletion)
                  case x: ChunkedRequestStart ⇒ HttpMessageStartEvent(ChunkedRequestStart(normalize(x.request)), closeAfterResponseCompletion)
                  case x                      ⇒ Http.MessageEvent(x)
                }
                eventPL(event)
                handleParsingResult(continue())

              case Result.Expect100Continue(continue) ⇒
                commandPL(Status100ContinueResponse)
                handleParsingResult(continue())

              case e @ Result.ParsingError(status, info) ⇒ handleError(status, info)

              case Result.IgnoreAllFurtherInput          ⇒
            }

          def handleError(status: StatusCode, info: ErrorInfo): Unit = {
            log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
            val msg = if (settings.verboseErrorMessages) info.formatPretty else info.summary
            commandPL(ResponsePartRenderingContext(HttpResponse(status, msg)))
            commandPL(Http.Close)
            parser = Result.IgnoreAllFurtherInput
          }

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case Tcp.Received(data: CompactByteString) ⇒
              try handleParsingResult(parser(data))
              catch {
                case NonFatal(e) ⇒ handleError(StatusCodes.BadRequest, ErrorInfo(e.getMessage.nullAsEmpty))
              }

            case ev ⇒ eventPL(ev)
          }
        }
    }
  }

  ////////////// EVENTS //////////////

  case class HttpMessageStartEvent(messagePart: HttpMessageStart, closeAfterResponseCompletion: Boolean) extends Event
}