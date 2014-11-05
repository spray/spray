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
import scala.concurrent.duration.Duration
import akka.actor.ActorRef
import akka.io.Tcp
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.can.rendering.ResponsePartRenderingContext
import spray.can.Http
import spray.http._
import spray.io._
import spray.util.Timestamp
import akka.io.Tcp.NoAck

private object ServerFrontend {

  trait Context extends PipelineContext {
    // the application-level request handler
    def handler: ActorRef
    def fastPath: Http.FastPath
  }

  def apply(serverSettings: ServerSettings): RawPipelineStage[Context] = {
    new RawPipelineStage[Context] {
      def apply(_context: Context, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines with OpenRequestComponent {
          // A reference to the first request in the chain whose response hasn't been fully ack'd
          var firstUnconfirmed: OpenRequest = EmptyOpenRequest

          // A reference into the unconfirmed list for the first request for which
          // no response was yet produced. This queue is strictly a suffix of the unconfirmed list.
          var firstOpenRequest: OpenRequest = EmptyOpenRequest

          var _requestTimeout: Duration = serverSettings.requestTimeout
          var _idleTimeout: Duration = serverSettings.idleTimeout
          var _timeoutTimeout: Duration = serverSettings.timeoutTimeout
          def context = _context
          val settings = serverSettings
          val downstreamCommandPL = commandPL
          def requestTimeout = _requestTimeout // required due to https://issues.scala-lang.org/browse/SI-6387
          def timeoutTimeout = _timeoutTimeout // required due to https://issues.scala-lang.org/browse/SI-6387

          val commandPipeline: CPL = {
            case Response(openRequest, command) if openRequest == firstOpenRequest ⇒
              commandPipeline(command) // "unpack" the command and recurse

            case Http.MessageCommand(wrapper: HttpMessagePartWrapper) if wrapper.messagePart.isInstanceOf[HttpResponsePart] ⇒
              // we can only see this command either after having "unpacked" a Response
              // or after an openRequest has begun dispatching its queued commands,
              // in both cases the firstOpenRequest member is valid and current
              val part = wrapper.messagePart match {
                case ChunkedResponseStart(response) if firstOpenRequest.request.method == HttpMethods.HEAD ⇒
                  // if HEAD requests are responded to with a chunked response we only sent the initial part
                  // and "cancel" the stream by "acking" with a fake Closed event
                  response.withAck(Http.Closed)
                case _ ⇒ wrapper
              }
              if (part.messagePart.isInstanceOf[HttpMessageEnd]) {
                firstOpenRequest = firstOpenRequest handleResponseEndAndReturnNextOpenRequest part
                firstUnconfirmed = firstUnconfirmed.nextIfNoAcksPending
              } else firstOpenRequest handleResponsePart part

            case Response(openRequest, command) ⇒
              // a response for a non-current openRequest has to be queued
              openRequest.enqueueCommand(command, context.sender)

            case ChunkHandlerRegistration(openRequest, handler) ⇒ openRequest.registerChunkHandler(handler)

            case CommandWrapper(SetRequestTimeout(timeout)) ⇒
              _requestTimeout = timeout
              if (_requestTimeout.isFinite() && _idleTimeout.isFinite() && _idleTimeout <= _requestTimeout) {
                val newIdleTimeout = timeout * 2
                context.log.debug("Auto-adjusting idle-timeout to {} after setting request-timeout to {}",
                  newIdleTimeout, timeout)
                commandPipeline(ConnectionTimeouts.SetIdleTimeout(newIdleTimeout))
              }

            case CommandWrapper(SetTimeoutTimeout(timeout)) ⇒ _timeoutTimeout = timeout

            case x @ ConnectionTimeouts.SetIdleTimeout(timeout) ⇒
              _idleTimeout = timeout
              if (_requestTimeout.isFinite() && _idleTimeout.isFinite() && _idleTimeout <= _requestTimeout)
                context.log.warning("Setting an idle-timeout < request-timeout effectively disables the request-timeout!")
              downstreamCommandPL(x)

            case cmd ⇒ downstreamCommandPL(cmd)
          }

          val eventPipeline: EPL = {
            case HttpMessageStartEvent(request: HttpRequest, closeAfterResponseCompletion) ⇒
              if (context.fastPath.isDefinedAt(request)) {
                val response =
                  try context.fastPath(request)
                  catch {
                    case NonFatal(e) ⇒
                      context.log.error(e, "Error during fastPath evaluation for request {}", request)
                      HttpResponse(StatusCodes.InternalServerError, StatusCodes.InternalServerError.defaultMessage)
                  }
                if (firstOpenRequest.isEmpty) commandPL {
                  val ack =
                    if (serverSettings.autoBackPressureEnabled) Tcp.NoAck
                    else Tcp.NoAck(AckEventWithReceiver(NoAck, response, context.self))
                  ResponsePartRenderingContext(response, request.method, request.protocol, closeAfterResponseCompletion, ack)
                }
                else throw new NotImplementedError("fastPath is not yet supported with pipelining enabled")

              } else openNewRequest(request, closeAfterResponseCompletion, WaitingForResponse(context.handler))

            case HttpMessageStartEvent(ChunkedRequestStart(request), closeAfterResponseCompletion) ⇒
              commandPL(Tcp.SuspendReading) // suspend reading until the handler is registered
              openNewRequest(request, closeAfterResponseCompletion, WaitingForChunkHandlerBuffering())

            case Http.MessageEvent(x: MessageChunk) ⇒
              firstOpenRequest handleMessageChunk x

            case Http.MessageEvent(x: ChunkedMessageEnd) ⇒
              firstOpenRequest handleChunkedMessageEnd x

            case x: AckEventWithReceiver ⇒
              firstUnconfirmed = firstUnconfirmed handleSentAckAndReturnNextUnconfirmed x

            case Tcp.CommandFailed(WriteCommandWithLastAck(AckEventWithReceiver(_, part, responseSender))) ⇒
              context.log.error("Could not write response part {}, aborting connection.", part)
              commandPL(Pipeline.Tell(responseSender, Http.SendFailed(part), context.self))
              commandPL(Tcp.Abort)

            case ev: Http.ConnectionClosed ⇒
              def sendClosed(receiver: ActorRef) = downstreamCommandPL(Pipeline.Tell(receiver, ev, context.self))

              val interestedParties = firstUnconfirmed.closedEventHandlers + context.handler
              interestedParties.foreach(sendClosed)

              eventPL(ev)

            case TickGenerator.Tick ⇒
              if (requestTimeout.isFinite())
                firstOpenRequest checkForTimeout Timestamp.now
              eventPL(TickGenerator.Tick)

            case Pipeline.ActorDeath(actor) if actor == context.handler ⇒
              context.log.debug("User-level connection handler died, closing connection")
              commandPL(Http.Close)

            case ev ⇒ eventPL(ev)
          }

          def openNewRequest(request: HttpRequest, closeAfterResponseCompletion: Boolean, state: RequestState): Unit = {
            val nextOpenRequest = new DefaultOpenRequest(request, closeAfterResponseCompletion, state)
            if (firstOpenRequest.isEmpty) {
              firstOpenRequest = nextOpenRequest
              if (firstUnconfirmed.isEmpty) firstUnconfirmed = nextOpenRequest
              else firstUnconfirmed.appendToEndOfChain(nextOpenRequest)
            } else firstOpenRequest.appendToEndOfChain(nextOpenRequest)
            nextOpenRequest.dispatchInitialRequestPartToHandler(context.sender)
          }
        }
    }
  }

  private object WriteCommandWithLastAck {
    def unapply(cmd: Tcp.Command): Option[Event] = {
      @tailrec def lastAck(c: Tcp.Command): Option[Event] =
        c match {
          case x: Tcp.SimpleWriteCommand  ⇒ Some(x.ack)
          case Tcp.CompoundWrite(_, tail) ⇒ lastAck(tail)
          case _                          ⇒ None
        }
      lastAck(cmd)
    }
  }
}
