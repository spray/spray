/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.can.server

import cc.spray.can.model._
import cc.spray.can.rendering.HttpResponsePartRenderingContext
import cc.spray.io._
import pipelines.{TickGenerator, MessageHandlerDispatch}
import akka.event.LoggingAdapter
import collection.mutable.Queue
import annotation.tailrec
import MessageHandlerDispatch._
import akka.spray.LazyActorRef
import akka.util.{Unsafe, Duration}
import akka.actor.{ActorPath, Terminated, ActorContext, ActorRef}

object ServerFrontend {

  def apply(settings: ServerSettings,
            messageHandler: MessageHandler,
            timeoutResponse: HttpRequest => HttpResponse,
            log: LoggingAdapter): DoublePipelineStage = {

    new DoublePipelineStage {
      def build(context: PipelineContext, commandPL: Pipeline[Command], eventPL: Pipeline[Event]): Pipelines = {
        new Pipelines with MessageHandlerDispatch {
          val openRequests = Queue.empty[RequestRecord]
          val openSends = Queue.empty[IoServer.Tell]
          val handlerCreator = messageHandlerCreator(messageHandler, context)
          var requestTimeout = settings.RequestTimeout
          var timeoutTimeout = settings.TimeoutTimeout

          val commandPipeline: CPL = {
            case part: HttpResponsePart with HttpMessageEndPart =>
              ensureRequestOpenFor(part)
              val rec = openRequests.head
              sendPart(part, rec)
              if (rec.hasQueuedResponses) {
                context.self ! rec.dequeue
              } else {
                openRequests.dequeue()
                if (!openRequests.isEmpty && openRequests.head.hasQueuedResponses)
                  context.self ! openRequests.head.dequeue
              }

            case part: HttpResponsePart =>
              ensureRequestOpenFor(part)
              val rec = openRequests.head
              rec.timestamp = 0L // disable request timeout checking once the first response part has come in
              sendPart(part, rec)

            case response: Response =>
              if (openRequests.isEmpty)
                log.warning("Received response without matching request, dropping... ")
              else if (response.rec == openRequests.head)
                commandPipeline(response.msg) // in order response, dispatch
              else
                response.rec.enqueue(response.msg) // out of order response, queue up

            case x: SetRequestTimeout => requestTimeout = x.timeout.toMillis
            case x: SetTimeoutTimeout => timeoutTimeout = x.timeout.toMillis

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case x: HttpRequest => dispatchRequestStart(x, x, System.currentTimeMillis)

            case x: ChunkedRequestStart => dispatchRequestStart(x, x.request, 0L)

            case x: MessageChunk => dispatchRequestChunk(x)

            case x: ChunkedMessageEnd =>
              if (openRequests.isEmpty) throw new IllegalStateException
              // only start request timeout checking after request has been completed
              openRequests.last.timestamp = System.currentTimeMillis
              dispatchRequestChunk(x)

            case x: HttpServer.AckSend =>
              if (openSends.isEmpty) throw new IllegalStateException
              commandPL(openSends.dequeue().copy(message = x))

            case x: HttpServer.Closed =>
              if (openSends.isEmpty && openRequests.isEmpty) {
                messageHandler match {
                  case _: SingletonHandler | _: PerConnectionHandler =>
                    commandPL(IoServer.Tell(handlerCreator(), x, context.self))
                  case _: PerMessageHandler =>
                    // per-message handlers do not receive Closed messages that are
                    // not related to a specific request, they need to cleanup themselves
                    // upon response sending or reception of the send confirmation
                }
              } else {
                openSends.foreach(tell => commandPL(tell.copy(message = x)))
                openRequests.foreach(r => commandPL(IoServer.Tell(r.handler, x, r.receiver)))
              }
              eventPL(x) // terminates the connection actor

            case TickGenerator.Tick =>
              checkForTimeouts()
              eventPL(TickGenerator.Tick)

            case x: CommandException =>
              log.warning("Received {}, closing connection ...", x)
              commandPL(HttpServer.Close(IoError(x)))

            case ev => eventPL(ev)
          }

          def sendPart(part: HttpResponsePart, rec: RequestRecord) {
            commandPL {
              import rec.request._
              HttpResponsePartRenderingContext(part, method, protocol, connectionHeader)
            }
            if (settings.AckSends) {
              // prepare the IoServer.Tell command to use for `AckSend` and potential `Closed` messages
              openSends.enqueue(IoServer.Tell(context.sender, (), rec.receiver))
            }
          }

          def ensureRequestOpenFor(part: HttpResponsePart) {
            if (openRequests.isEmpty)
              throw new IllegalStateException("Received ResponsePart '" + part + "' for non-existing request")
          }

          def dispatchRequestStart(part: HttpRequestPart, request: HttpRequest, timestamp: Long) {
            val rec = new RequestRecord(request, handlerCreator(), timestamp)
            rec.receiver =
              if (settings.DirectResponding) context.self
              else new RequestRef(rec, context.connectionActorContext)
            openRequests += rec
            val partToDispatch: HttpRequestPart =
              if (request.method != HttpMethods.HEAD || !settings.TransparentHeadRequests) part
              else if (part.isInstanceOf[HttpRequest]) request.copy(method = HttpMethods.GET)
              else ChunkedRequestStart(request.copy(method = HttpMethods.GET))
            commandPL(IoServer.Tell(rec.handler, partToDispatch, rec.receiver))
          }

          def dispatchRequestChunk(part: HttpRequestPart) {
            if (openRequests.isEmpty) // part before start shouldn't be allowed by the request parsing stage
              throw new IllegalStateException
            val rec = openRequests.last
            commandPL(IoServer.Tell(rec.handler, part, rec.receiver))
          }

          @tailrec
          def checkForTimeouts() {
            if (!openRequests.isEmpty && requestTimeout > 0) {
              val rec = openRequests.head
              if (rec.timestamp > 0) {
                if (rec.timestamp + requestTimeout < System.currentTimeMillis) {
                  val timeoutHandler = if (settings.TimeoutHandler.isEmpty) rec.handler
                                       else context.connectionActorContext.actorFor(settings.TimeoutHandler)
                  commandPipeline(IoServer.Tell(timeoutHandler, RequestTimeout(rec.request), rec.receiver))
                  // we record the time of the Timeout dispatch as negative timestamp value
                  rec.timestamp = -System.currentTimeMillis
                }
              } else if (rec.timestamp < 0 && timeoutTimeout > 0) {
                if (-rec.timestamp + timeoutTimeout < System.currentTimeMillis) {
                  commandPipeline(timeoutResponse(rec.request))
                  checkForTimeouts() // check potentially pending requests for timeouts
                }
              }
            }
          }
        }
      }
    }
  }

  private class RequestRecord(val request: HttpRequest, val handler: ActorRef, var timestamp: Long) {
    var receiver: ActorRef = _
    private var responses: Queue[Command] = _
    def enqueue(msg: Command) {
      if (responses == null) responses = Queue(msg)
      else responses.enqueue(msg)
    }
    def hasQueuedResponses = responses != null && !responses.isEmpty
    def dequeue = responses.dequeue()
  }

  private class Response(val rec: RequestRecord, val msg: Command) extends Command

  object RequestRef {
    private val responseStateOffset = Unsafe.instance.objectFieldOffset(
      classOf[RequestRef].getDeclaredField("_responseStateDoNotCallMeDirectly"))

    sealed trait ResponseState
    case object Uncompleted extends ResponseState
    case object Completed extends ResponseState
    case object Chunking extends ResponseState
  }

  private class RequestRef(rec: RequestRecord, context: ActorContext) extends LazyActorRef(context.self) {
    import RequestRef._
    @volatile private[this] var _responseStateDoNotCallMeDirectly: ResponseState = Uncompleted
    protected def handle(message: Any, sender: ActorRef) {
      message match {
        case x: HttpResponse         => dispatch(x, sender, Uncompleted, Completed)
        case x: ChunkedResponseStart => dispatch(x, sender, Uncompleted, Chunking)
        case x: MessageChunk         => dispatch(x, sender, Chunking, Chunking)
        case x: ChunkedMessageEnd    => dispatch(x, sender, Chunking, Completed)
        case x: Command              => dispatch(x, sender)
        case Terminated(ref) if ref == context.self => stop() // cleanup when the connection died
        case x =>
          context.system.log.warning("Illegal response " + x + " to HTTP request to '" + rec.request.uri + "'")
          provider.deadLetters ! x
      }
    }
    private def dispatch(part: HttpResponsePart, sender: ActorRef,
                         expectedState: ResponseState, newState: ResponseState) {
      if (Unsafe.instance.compareAndSwapObject(this, responseStateOffset, expectedState, newState)) {
        context.self.tell(new Response(rec, part), sender)
        if (newState == Completed) stop()
      } else {
        context.system.log.warning("Cannot dispatch " + part.getClass.getSimpleName +
          " as response (part) for request to '" + rec.request.uri + "' since current response state is '" +
          Unsafe.instance.getObjectVolatile(this, responseStateOffset) + "' but should be '" + expectedState + "'")
        provider.deadLetters ! part
      }
    }
    private def dispatch(cmd: Command, sender: ActorRef) {
      context.self.tell(new Response(rec, cmd), sender)
    }

    override protected def register(path: ActorPath) {
      super.register(path)
      context.watch(context.self)
    }

    override protected def unregister(path: ActorPath) {
      super.unregister(path)
      context.unwatch(context.self)
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

  ////////////// EVENTS //////////////

  case class RequestTimeout(request: HttpRequest) extends Event

}