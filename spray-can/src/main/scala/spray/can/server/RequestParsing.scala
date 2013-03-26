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

import scala.annotation.tailrec
import scala.util.control.NonFatal
import akka.io.Tcp
import akka.util.{ ByteString, ByteIterator }
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.can.{ RequestLine, Http }
import spray.can.parsing._
import spray.http._
import spray.io._
import spray.http.parser.HttpParser

object RequestParsing {

  lazy val continue = "HTTP/1.1 100 Continue\r\n\r\n".getBytes("ASCII")

  def apply(settings: ServerSettings): RawPipelineStage[SslTlsContext] = {
    new RawPipelineStage[SslTlsContext] {
      val startParser = new EmptyRequestParser(settings.parserSettings)

      def apply(context: SslTlsContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import context.log
          val https = settings.sslEncryption && context.sslEngine.isDefined
          var currentParsingState: ParsingState = startParser

          @tailrec
          final def parse(data: ByteIterator) {
            currentParsingState match {
              case x: IntermediateState ⇒
                if (data.hasNext) {
                  currentParsingState = x.read(data)
                  parse(data)
                } // else wait for more input

              case x @ CompleteMessageState(RequestLine(method, uri, proto), headers, connectionHeader, _, _) ⇒
                val req = HttpRequest(method, Uri.parseHttpRequestTarget(uri), parseHeaders(headers), x.entity, proto)
                eventPL(HttpMessageStartEvent(req.withEffectiveUri(https), connectionHeader))
                currentParsingState = startParser
                parse(data)

              case x @ ChunkedStartState(RequestLine(method, uri, proto), headers, connectionHeader, _) ⇒
                val req = HttpRequest(method, Uri.parseHttpRequestTarget(uri), parseHeaders(headers), x.entity, proto)
                eventPL(HttpMessageStartEvent(ChunkedRequestStart(req.withEffectiveUri(https)), connectionHeader))
                currentParsingState = new ChunkParser(settings.parserSettings)
                parse(data)

              case ChunkedChunkState(extensions, body) ⇒
                eventPL(Http.MessageEvent(MessageChunk(body, extensions)))
                currentParsingState = new ChunkParser(settings.parserSettings)
                parse(data)

              case ChunkedEndState(extensions, trailer) ⇒
                eventPL(Http.MessageEvent(ChunkedMessageEnd(extensions, trailer)))
                currentParsingState = startParser
                parse(data)

              case Expect100ContinueState(nextState) ⇒
                commandPL(Tcp.Write(ByteString(continue)))
                currentParsingState = nextState
                parse(data)

              case ErrorState.Dead ⇒ // if we already handled the error state we ignore all further input

              case x: ErrorState ⇒
                handleParseError(x)
                currentParsingState = ErrorState.Dead // set to "special" ErrorState that ignores all further input
            }
          }

          def handleParseError(state: ErrorState) {
            log.warning("Illegal request, responding with status {} and '{}'", state.status, state.message)
            val msg = if (settings.verboseErrorMessages) state.message else state.summary
            val response = HttpResponse(state.status, msg)

            // In case of a request parsing error we probably stopped reading the request somewhere in between,
            // where we cannot simply resume. Resetting to a known state is not easy either,
            // so we need to close the connection to do so.
            commandPL(HttpResponsePartRenderingContext(response))
            commandPL(Http.Close)
          }

          def parseHeaders(headers: List[HttpHeader]) = {
            val (errors, parsed) = HttpParser.parseHeaders(headers)
            if (!errors.isEmpty) errors.foreach(e ⇒ log.warning(e.formatPretty))
            parsed
          }

          val commandPipeline = commandPL

          val eventPipeline: EPL = {
            case Tcp.Received(data) ⇒
              try parse(data.iterator)
              catch {
                case NonFatal(e) ⇒ handleParseError(ErrorState(e.getMessage))
              }

            case ev ⇒ eventPL(ev)
          }
        }
    }
  }

  ////////////// EVENTS //////////////

  case class HttpMessageStartEvent(messagePart: HttpMessageStart, connectionHeader: Option[String]) extends Event
}