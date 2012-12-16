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

import scala.concurrent.duration.{Duration, FiniteDuration}
import akka.event.{Logging, LoggingAdapter}
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.can.{HttpEvent, HttpCommand}
import spray.util.ConnectionCloseReasons._
import spray.http._
import spray.io._


object ServerFrontend {

  def apply(serverSettings: ServerSettings,
            messageHandler: MessageHandler,
            timeoutResponse: HttpRequest => HttpResponse,
            log: LoggingAdapter): PipelineStage = {
    val warning = TaggableLog(log, Logging.WarningLevel)
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines with OpenRequestComponent {
          var firstOpenRequest: OpenRequest = EmptyOpenRequest
          var firstUnconfirmed: OpenRequest = EmptyOpenRequest
          var _requestTimeout: Long = serverSettings.RequestTimeout
          var _timeoutTimeout: Long = serverSettings.TimeoutTimeout
          def requestTimeout = _requestTimeout // required due to https://issues.scala-lang.org/browse/SI-6387
          def timeoutTimeout = _timeoutTimeout // required due to https://issues.scala-lang.org/browse/SI-6387
          val handlerCreator = messageHandler(context)
          val connectionActorContext = context.connectionActorContext
          val settings = serverSettings
          val downstreamCommandPL = commandPL
          val createTimeoutResponse = timeoutResponse

          // per-message handlers do not receive Closed messages that are
          // not related to a specific request, they need to cleanup themselves
          // upon response sending or reception of the send confirmation
          val handlerReceivesClosedEvents = !messageHandler.isInstanceOf[PerMessageHandler]

          def warn(msg: String) { warning.log(context.connection.tag, msg) }

          val commandPipeline: CPL = {
            case Response(openRequest, command) if openRequest == firstOpenRequest =>
              commandPipeline(command) // "unpack" the command and recurse

            case HttpCommand(wrapper: HttpMessagePartWrapper) if wrapper.messagePart.isInstanceOf[HttpResponsePart] =>
              // we can only see this command either after having "unpacked" a Response
              // or after an openRequest has begun dispatching its queued commands,
              // in both cases the firstOpenRequest member is valid and current
              val part = wrapper.messagePart match {
                case ChunkedResponseStart(response) if firstOpenRequest.request.method == HttpMethods.HEAD =>
                  // if HEAD requests are responded to with a chunked response we only sent the initial part
                  // and "cancel" the stream by "acking" with a fake Closed event
                  response.withSentAck(IOBridge.Closed(context.connection, CleanClose))
                case _ => wrapper
              }
              if (part.messagePart.isInstanceOf[HttpMessageEnd]) {
                firstOpenRequest = firstOpenRequest.handleResponseEndAndReturnNextOpenRequest(part)
                firstUnconfirmed = firstUnconfirmed.nextIfNoAcksPending
              } else firstOpenRequest.handleResponsePart(part)

            case Response(openRequest, command) =>
              // a response for a non-current openRequest has to be queued
              openRequest.enqueueCommand(command)

            case SetRequestTimeout(timeout) =>
              _requestTimeout = timeout.toMillis

            case SetTimeoutTimeout(timeout) =>
              _timeoutTimeout = timeout.toMillis

            case cmd => downstreamCommandPL(cmd)
          }

          val eventPipeline: EPL = {
            case HttpMessageStartEvent(request: HttpRequest, connectionHeader) =>
              openNewRequest(request, connectionHeader, System.currentTimeMillis)

            case HttpMessageStartEvent(ChunkedRequestStart(request), connectionHeader) =>
              openNewRequest(request, connectionHeader, 0L)

            case HttpEvent(x: MessageChunk) =>
              firstOpenRequest.handleMessageChunk(x)

            case HttpEvent(x: ChunkedMessageEnd) =>
              firstOpenRequest.handleChunkedMessageEnd(x)

            case x: AckEventWithReceiver =>
              firstUnconfirmed = firstUnconfirmed.handleSentAckAndReturnNextUnconfirmed(x)

            case x: HttpServer.Closed =>
              if (firstUnconfirmed.isEmpty)
                firstOpenRequest.handleClosed(x) // dispatches to the handler if no request is open
              else
                firstUnconfirmed.handleClosed(x) // also includes the firstOpenRequest and beyond
              eventPL(x) // terminates the connection actor

            case TickGenerator.Tick =>
              if (requestTimeout > 0L)
                firstOpenRequest.checkForTimeout(System.currentTimeMillis())
              eventPL(TickGenerator.Tick)

            case x: CommandException =>
              log.warning("Received {}, closing connection ...", x)
              downstreamCommandPL(HttpServer.Close(ProtocolError(x.toString)))

            case ev => eventPL(ev)
          }

          def openNewRequest(request: HttpRequest, connectionHeader: Option[String], timestamp: Long) {
            val nextOpenRequest = new DefaultOpenRequest(request, connectionHeader, timestamp)
            firstOpenRequest = firstOpenRequest.appendToEndOfChain(nextOpenRequest)
            nextOpenRequest.dispatchInitialRequestPartToHandler()
            if (firstUnconfirmed.isEmpty) firstUnconfirmed = firstOpenRequest
          }
        }
      }
  }

  ////////////// COMMANDS //////////////

  case class SetRequestTimeout(timeout: FiniteDuration) extends Command {
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }

  case class SetTimeoutTimeout(timeout: FiniteDuration) extends Command {
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }

}