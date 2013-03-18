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

import java.net.InetSocketAddress
import scala.collection.immutable
import scala.concurrent.duration.Duration
import akka.actor.{ReceiveTimeout, Status, ActorRef, Actor}
import akka.io.Inet
import spray.util.SprayActorLogging
import spray.can.client.HttpHostConnector._
import spray.can.Http
import spray.io.ClientSSLEngineProvider
import spray.http._

private[client] class HttpHostConnection(remoteAddress: InetSocketAddress,
                                         options: immutable.Traversable[Inet.SocketOption],
                                         idleTimeout: Duration,
                                         clientConnectionSettingsGroup: ActorRef)
                                        (implicit sslEngineProvider: ClientSSLEngineProvider)
  extends Actor with SprayActorLogging {

  private[this] var openRequests = immutable.Queue.empty[RequestContext]
  private[this] var closeAfterCurrentResponseEnd = false
  private[this] var closingTrigger: Option[AnyRef] = None

  def receive: Receive = unconnected

  def unconnected: Receive = {
    context.setReceiveTimeout(idleTimeout)

    {
      case ctx: RequestContext =>
        log.debug("Attempting new connection to {}", remoteAddress)
        clientConnectionSettingsGroup ! Http.Connect(remoteAddress, None, options, None, sslEngineProvider)
        openRequests = openRequests.enqueue(ctx)
        context.setReceiveTimeout(Duration.Undefined)
        context become connecting

      case _: Http.CloseCommand | _: Http.ConnectionClosed => // ignore

      case ReceiveTimeout =>
        if (openRequests.isEmpty) {
          log.debug("Initiating idle shutdown")
          context.parent ! DemandIdleShutdown
          context become { // after having initiated our shutdown we must be bounce all requests
            case ctx: RequestContext => context.parent ! ctx
          }
        }
    }
  }

  def connecting: Receive = {
    case _: Http.Connected =>
      log.debug("Connection to {} established, dispatching {} pending requests", remoteAddress, openRequests.size)
      openRequests foreach dispatchToServer(sender)
      closingTrigger = None
      context become connected(sender)

    case ctx: RequestContext => openRequests = openRequests.enqueue(ctx)

    case cmd: Http.CloseCommand => context become {
      case _: Http.Connected =>
        sender ! cmd
        context.parent ! Disconnected(openRequests.size)
        clearOpenRequests("Connection actively closed", retry = false)
        context become unconnected

      case _: RequestContext | _: Http.CommandFailed => // ignore
    }

    case _: Http.CommandFailed =>
      log.debug("Connection attempt failed")
      context.parent ! Disconnected(openRequests.size)
      clearOpenRequests("Connection attempt failed", retry = false)
      context become unconnected
  }

  def connected(httpConnection: ActorRef): Receive = {
    case part: HttpResponsePart if openRequests.nonEmpty =>
      val RequestContext(request, _, commander) = openRequests.head
      if (log.isDebugEnabled) log.debug("Delivering {} for {}", formatResponse(part), format(request))
      commander ! part
      context.parent ! RequestCompleted
      if (part.isInstanceOf[HttpMessageStart])
        closeAfterCurrentResponseEnd =
          part.asInstanceOf[HttpMessageStart].message.asInstanceOf[HttpResponse].connectionCloseExpected
      if (part.isInstanceOf[HttpMessageEnd]) {
        openRequests = openRequests.tail
        if (closeAfterCurrentResponseEnd) {
          log.debug("Closing connection as indicated by last response")
          httpConnection ! Http.Close
          context.parent ! Disconnected(openRequests.size)
          clearOpenRequests("Premature connection close (the server doesn't appear to support request pipelining)",
            retry = true)
          context become unconnected
        }
      }

    case x: HttpResponsePart =>
      log.warning("Received unexpected response for non-existing request: {}, dropping", x)

    case ev@ Http.SendFailed(part) =>
      log.error("Sending {} failed, closing connection", format(part))
      httpConnection ! Http.Close
      closingTrigger = Some(ev)

    case ev@ Timedout(part) =>
      log.warning("{} timed out, closing connection", format(part))
      httpConnection ! Http.Close
      closingTrigger = Some(ev)

    case ev@ Http.CommandFailed(cmd) =>
      log.warning("Received {}, closing connection", ev)
      httpConnection ! Http.Close
      closingTrigger = Some(ev)

    case cmd: Http.CloseCommand =>
      httpConnection ! cmd
      closingTrigger = Some(cmd)

    case ev: Http.ConnectionClosed =>
      context.parent ! Disconnected(openRequests.size)
      if (openRequests.nonEmpty) {
        val (errorMsg, retry) = closingTrigger match {
          case None => ev.toString -> true
          case Some(_: Http.SendFailed) => "Error sending request (part)" -> true
          case Some(_: Timedout) => "Request timeout" -> true
          case Some(_: Http.CommandFailed) => "Command error" -> true
          case Some(_: Http.CloseCommand) => "Connection actively closed" -> false
          case _ => throw new IllegalStateException
        }
        clearOpenRequests(errorMsg, retry)
      } else log.debug(s"Received $ev event")
      context become unconnected

    case ctx: RequestContext =>
      dispatchToServer(httpConnection)(ctx)
      openRequests = openRequests.enqueue(ctx)
  }

  def dispatchToServer(httpConnection: ActorRef)(ctx: RequestContext): Unit = {
    if (log.isDebugEnabled) log.debug("Dispatching {} across connection {}", format(ctx.request), httpConnection)
    httpConnection ! ctx.request
  }

  def clearOpenRequests(errorMsg: String, retry: Boolean): Unit = {
    openRequests.foreach {
      case ctx@ RequestContext(request, retriesLeft, _) if retry && request.canBeRetried && retriesLeft > 0 =>
        log.warning("{} in response to {} with {} retries left, retrying...", errorMsg, format(request), retriesLeft)
        context.parent ! ctx

      case RequestContext(request, _, commander) =>
        log.debug("{} in response to {} with no retries left, dispatching error...", errorMsg, format(request))
        commander ! Status.Failure(new RuntimeException(errorMsg))
    }
    openRequests = immutable.Queue.empty[RequestContext]
  }

  def format(part: HttpMessagePart) = part match {
    case x: HttpRequestPart with HttpMessageStart =>
      val request = x.message.asInstanceOf[HttpRequest]
      s"${request.method} request to ${request.uri}"
    case MessageChunk(body, _) => body.length.toString + " byte request chunk"
    case x => x.toString
  }

  def formatResponse(part: HttpResponsePart) = part match {
    case HttpResponse(status, _, _, _) => status.value.toString + " response"
    case ChunkedResponseStart(HttpResponse(status, _, _, _)) => status.value.toString + " response start"
    case MessageChunk(body, _) => body.length.toString + " byte response chunk"
    case x => x.toString
  }
}
