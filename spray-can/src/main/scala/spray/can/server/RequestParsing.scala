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

package spray.can.server

import scala.annotation.tailrec
import akka.util.NonFatal
import akka.io.Tcp
import akka.util.ByteString
import spray.can.rendering.ResponsePartRenderingContext
import spray.can.Http
import spray.can.parsing._
import spray.http._
import spray.util._
import spray.io._

object RequestParsing {

  val continue = ByteString("HTTP/1.1 100 Continue\r\n\r\n")

  def apply(settings: ServerSettings): RawPipelineStage[SslTlsContext] = {
    val rootParser = new HttpRequestPartParser(settings.parserSettings)()
    new RawPipelineStage[SslTlsContext] {
      def apply(context: SslTlsContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          val https = settings.sslEncryption && context.sslEngine.isDefined
          val parser = rootParser.copyWith { errorInfo ⇒
            if (settings.parserSettings.illegalHeaderWarnings)
              log.warning(errorInfo.withSummaryPrepended("Illegal request header").formatPretty)
          }

          @tailrec def parse(data: ByteString): Unit =
            if (!data.isEmpty) parser.parse(data) match {
              case Result.Ok(part, remainingData, closeAfterResponseCompletion) ⇒
                eventPL {
                  part match {
                    case x: HttpRequest ⇒
                      HttpMessageStartEvent(x.withEffectiveUri(https), closeAfterResponseCompletion)
                    case x: ChunkedRequestStart ⇒
                      HttpMessageStartEvent(ChunkedRequestStart(x.request.withEffectiveUri(https)),
                        closeAfterResponseCompletion)
                    case x ⇒
                      Http.MessageEvent(x)
                  }
                }
                parse(remainingData)

              case Result.NeedMoreData               ⇒ // just wait for the next packet

              case Result.ParsingError(status, info) ⇒ handleError(status, info)

              case Result.Expect100Continue(remainingData) ⇒
                commandPL(Tcp.Write(continue))
                parse(remainingData)
            }

          def handleError(status: StatusCode, info: ErrorInfo): Unit = {
            log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
            val msg = if (settings.verboseErrorMessages) info.formatPretty else info.summary
            commandPL(ResponsePartRenderingContext(HttpResponse(status, msg)))
            commandPL(Http.Close)
          }

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case Tcp.Received(data: ByteString) ⇒
              try parse(data)
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
