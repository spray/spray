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

import java.net.InetSocketAddress
import scala.collection.immutable
import scala.collection.immutable.Queue
import scala.concurrent.duration.Duration
import akka.actor._
import akka.io.Inet
import spray.util.SprayActorLogging
import spray.can.client.HttpHostConnector._
import spray.can.Http
import spray.io.ClientSSLEngineProvider
import spray.http._

private[client] class HttpHostConnectionSlot(remoteAddress: InetSocketAddress,
                                             options: immutable.Traversable[Inet.SocketOption],
                                             idleTimeout: Duration,
                                             clientConnectionSettingsGroup: ActorRef)(implicit sslEngineProvider: ClientSSLEngineProvider)
    extends Actor with SprayActorLogging {

  // we cannot sensibly recover from crashes
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def receive: Receive = unconnected

  def unconnected: Receive = {
    context.setReceiveTimeout(idleTimeout)

    {
      case ctx: RequestContext ⇒
        log.debug("Attempting new connection to {}", remoteAddress)
        clientConnectionSettingsGroup ! Http.Connect(remoteAddress, None, options, None)
        context.setReceiveTimeout(Duration.Undefined)
        context.become(connecting(Queue(ctx)))

      case _: Http.CloseCommand ⇒ context.stop(self)

      case ReceiveTimeout ⇒
        log.debug("Initiating idle shutdown")
        context.parent ! DemandIdleShutdown
        context.become { // after having initiated our shutdown we must bounce all requests
          case ctx: RequestContext  ⇒ context.parent ! ctx
          case _: Http.CloseCommand ⇒ context.stop(self)
        }

      case Terminated(conn) ⇒
      // ignore, may happen if in closing we unwatch too late
      // and this message is already in the mailbox
    }
  }

  def connecting(openRequests: Queue[RequestContext], aborted: Option[Http.CloseCommand] = None): Receive = {
    case _: Http.Connected if aborted.isDefined ⇒
      sender ! aborted.get
      openRequests foreach clear("Connection actively closed", retry = false)
      context.become(terminating(context.watch(sender)))

    case _: Http.Connected ⇒
      log.debug("Connection to {} established, dispatching {} pending requests", remoteAddress, openRequests.size)
      openRequests foreach dispatchToServer(sender)
      context.become(connected(context.watch(sender), openRequests))

    case ctx: RequestContext    ⇒ context.become(connecting(openRequests.enqueue(ctx)))

    case cmd: Http.CloseCommand ⇒ context.become(connecting(openRequests, aborted = Some(cmd)))

    case _: Http.CommandFailed ⇒
      log.debug("Connection attempt failed")
      openRequests foreach clear("Connection attempt failed", retry = false)
      if (aborted.isEmpty) {
        context.parent ! Disconnected(openRequests.size)
        context.become(unconnected)
      } else context.stop(self)
  }

  def connected(httpConnection: ActorRef, openRequests: Queue[RequestContext],
                closeAfterResponseEnd: Boolean = false): Receive = {
    case part: HttpResponsePart if openRequests.nonEmpty ⇒
      val RequestContext(request, _, commander) = openRequests.head
      if (log.isDebugEnabled) log.debug("Delivering {} for {}", formatResponse(part), format(request))
      commander ! part
      def handleResponseCompletion(): Unit = {
        context.parent ! RequestCompleted
        context.become(connected(httpConnection, openRequests.tail))
      }
      part match {
        case x: HttpResponse ⇒ handleResponseCompletion()
        case ChunkedResponseStart(x: HttpResponse) ⇒
          context.become(connected(httpConnection, openRequests, x.connectionCloseExpected))
        case _: MessageChunk      ⇒ // nothing to do
        case _: ChunkedMessageEnd ⇒ handleResponseCompletion()
      }

    case x: HttpResponsePart ⇒
      log.warning("Received unexpected response for non-existing request: {}, dropping", x)

    case ctx: RequestContext ⇒
      dispatchToServer(httpConnection)(ctx)
      context.become(connected(httpConnection, openRequests.enqueue(ctx), closeAfterResponseEnd))

    case ev @ Http.SendFailed(part) ⇒
      log.debug("Sending {} failed, closing connection", format(part))
      httpConnection ! Http.Close
      context.become(closing(httpConnection, openRequests, "Error sending request (part)", retry = true))

    case ev: Http.CommandFailed ⇒
      log.debug("Received {}, closing connection", ev)
      httpConnection ! Http.Close
      context.become(closing(httpConnection, openRequests, "Command error", retry = true))

    case ev @ Timedout(part) ⇒
      log.debug("{} timed out, closing connection", format(part))
      context.become(closing(httpConnection, openRequests, "Request timeout", retry = true))

    case cmd: Http.CloseCommand ⇒
      httpConnection ! cmd
      openRequests foreach clear(s"Connection actively closed ($cmd)", retry = false)
      context.become(terminating(httpConnection))

    case ev: Http.ConnectionClosed ⇒
      context.parent ! Disconnected(openRequests.size)
      val errorMsgForOpenRequests = ev match {
        case Http.PeerClosed ⇒ "Premature connection close (the server doesn't appear to support request pipelining)"
        case x               ⇒ x.toString
      }
      openRequests foreach clear(errorMsgForOpenRequests, retry = true)
      context.unwatch(httpConnection)
      context.become(unconnected)

    case Terminated(`httpConnection`) ⇒
      context.parent ! Disconnected(openRequests.size)
      openRequests foreach clear("Unexpected connection termination", retry = true)
      context.become(unconnected)
  }

  def closing(httpConnection: ActorRef, openRequests: Queue[RequestContext], errorMsg: String,
              retry: Boolean): Receive = {

    case ev @ (_: Http.ConnectionClosed | Terminated(`httpConnection`)) ⇒
      context.parent ! Disconnected(openRequests.size)
      openRequests foreach clear(errorMsg, retry)
      context.unwatch(httpConnection)
      context.become(unconnected)
  }

  def terminating(httpConnection: ActorRef): Receive = {
    case _: Http.ConnectionClosed     ⇒ // ignore
    case Terminated(`httpConnection`) ⇒ context.stop(self)
  }

  def clear(errorMsg: String, retry: Boolean): RequestContext ⇒ Unit = {
    case ctx @ RequestContext(request, retriesLeft, _) if retry && request.canBeRetried && retriesLeft > 0 ⇒
      log.warning("{} in response to {} with {} retries left, retrying...", errorMsg, format(request), retriesLeft)
      context.parent ! ctx.copy(retriesLeft = retriesLeft - 1)

    case RequestContext(request, _, commander) ⇒
      log.warning("{} in response to {} with no retries left, dispatching error...", errorMsg, format(request))
      commander ! Status.Failure(new RuntimeException(errorMsg))
  }

  def dispatchToServer(httpConnection: ActorRef)(ctx: RequestContext): Unit = {
    if (log.isDebugEnabled) log.debug("Dispatching {} across connection {}", format(ctx.request), httpConnection)
    httpConnection ! ctx.request
  }

  def format(part: HttpMessagePart) = part match {
    case x: HttpRequestPart with HttpMessageStart ⇒
      val request = x.message.asInstanceOf[HttpRequest]
      s"${request.method} request to ${request.uri}"
    case MessageChunk(body, _) ⇒ body.length.toString + " byte request chunk"
    case x                     ⇒ x.toString
  }

  def formatResponse(part: HttpResponsePart) = part match {
    case HttpResponse(status, _, _, _) ⇒ status.value.toString + " response"
    case ChunkedResponseStart(HttpResponse(status, _, _, _)) ⇒ status.value.toString + " response start"
    case MessageChunk(body, _) ⇒ body.length.toString + " byte response chunk"
    case x ⇒ x.toString
  }
}
