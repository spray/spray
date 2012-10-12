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
import akka.util.Duration
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.can.{HttpEvent, HttpCommand}
import spray.util.IOError
import spray.http._
import spray.io._


object ServerFrontend {

  def apply(serverSettings: ServerSettings,
            messageHandler: MessageHandler,
            timeoutResponse: HttpRequest => HttpResponse,
            loggingAdapter: LoggingAdapter): PipelineStage = {

    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = {
        new Pipelines with OpenRequestComponent {
          private var firstOpenRequest: OpenRequest = EmptyOpenRequest
          private var firstUnconfirmed: OpenRequest = EmptyOpenRequest
          private var _requestTimeout: Long = serverSettings.RequestTimeout
          private var _timeoutTimeout: Long = serverSettings.TimeoutTimeout
          def requestTimeout = _requestTimeout // required due to https://issues.scala-lang.org/browse/SI-6387
          def timeoutTimeout = _timeoutTimeout // required due to https://issues.scala-lang.org/browse/SI-6387
          val handlerCreator = messageHandler(context)
          val connectionActorContext = context.connectionActorContext
          val log = loggingAdapter
          val settings = serverSettings
          val downstreamCommandPL = commandPL
          val createTimeoutResponse = timeoutResponse

          // per-message handlers do not receive Closed messages that are
          // not related to a specific request, they need to cleanup themselves
          // upon response sending or reception of the send confirmation
          val handlerReceivesClosedEvents = !messageHandler.isInstanceOf[PerMessageHandler]

          val commandPipeline: CPL = {
            case Response(openRequest, command) if openRequest == firstOpenRequest =>
              commandPipeline(command) // "unpack" the command and recurse

            case HttpCommand(x: HttpMessagePartWrapper) if x.messagePart.isInstanceOf[HttpResponsePart] =>
              // we can only see this command either after having "unpacked" a Response
              // or after an openRequest has begun dispatching its queued commands,
              // in both cases the firstOpenRequest member is valid and current
              if (x.messagePart.isInstanceOf[HttpMessageEnd]) {
                firstOpenRequest = firstOpenRequest.handleResponseEndAndReturnNextOpenRequest(x)
                firstUnconfirmed = firstUnconfirmed.nextIfNoAcksPending
              } else firstOpenRequest.handleResponsePart(x)

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
              downstreamCommandPL(HttpServer.Close(IOError(x)))

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
  }

  ////////////// COMMANDS //////////////

  case class SetRequestTimeout(timeout: Duration) extends Command {
    require(timeout.isFinite, "timeout must not be infinite, set to zero to disable")
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }

  case class SetTimeoutTimeout(timeout: Duration) extends Command {
    require(timeout.isFinite, "timeout must not be infinite, set to zero to disable")
    require(timeout >= Duration.Zero, "timeout must not be negative")
  }

}