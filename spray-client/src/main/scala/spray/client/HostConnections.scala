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

package spray.client

import scala.collection.mutable
import akka.actor.{ActorRef, Props, Status}
import spray.can.client.HttpClientConnection
import spray.util._
import spray.io._
import spray.http._


private[client] trait HostConnections {
  this: HttpHostConnector =>

  sealed abstract class  HostConnection {
    def index: Int

    // the number of open requests on this connection
    // if the connection is unconnected the method returns -1
    def pendingResponses: Int

    // dispatches the given request context to this connection.
    def dispatch(reqCtx: RequestContext)

    def resetConnection() {
      updateHostConnection(UnconnectedHostConnection(index))
    }

    def handleConnectionActorDeath(connectionActor: ActorRef)
  }

  case class UnconnectedHostConnection(index: Int) extends HostConnection {
    def pendingResponses: Int = -1
    def dispatch(reqCtx: RequestContext) {
      debug.log(defaultConnectionTag, "Opening connection {} to {}:{}", index + 1, host, port)
      // TODO: prefix actor name with "c" + (index+1) + "-" once akka allows custom name prefixes (with random endings)
      val connectionActor = context.actorOf(Props(new HttpClientConnection(clientConnectionSettings)))
      context.watch(connectionActor)
      val next = new ConnectingHostConnection(index, connectionActor)(reqCtx)
      connectionActor.tell(HttpClientConnection.Connect(host, port, tagForConnection(index)), Reply.withContext(next))
      updateHostConnection(next)
    }
    def handleConnectionActorDeath(connectionActor: ActorRef) {}
  }

  case class ConnectingHostConnection(index: Int, connectionActor: ActorRef)(firstReqCtx: RequestContext)
    extends HostConnection {

    private val pendingRequests = mutable.ListBuffer(firstReqCtx)

    def pendingResponses: Int = pendingRequests.size
    def dispatch(reqCtx: RequestContext) {
      pendingRequests += reqCtx
    }
    def connected(connection: Connection) {
      debug.log(connection.tag, "Connection {} established, dispatching {} pending requests", index, pendingResponses)
      updateHostConnection(new ConnectedHostConnection(index, connectionActor, connection)(pendingRequests.toList))
    }
    def connectFailed(error: Status.Failure) {
      pendingRequests.foreach(ctx => ctx.commander ! error)
      resetConnection()
    }

    def handleConnectionActorDeath(connectionActor: ActorRef) {
      if (connectionActor == this.connectionActor) updateHostConnection(new UnconnectedHostConnection(index))
    }
  }

  case class ConnectedHostConnection(index: Int, connectionActor: ActorRef, connection: Connection)
                                    (pendingRequests: List[RequestContext]) extends HostConnection {
    private[this] val openRequests = mutable.Queue.empty[RequestContext]
    private[this] var closeAfterChunkedMessageEnd = false
    def pendingResponses: Int = openRequests.size

    pendingRequests.foreach(dispatch)

    def dispatch(reqCtx: RequestContext) {
      if (debug.enabled) debug.log(connection.tag, "Dispatching {} across connection {}", format(reqCtx.request), index)
      connection.handler.tell(reqCtx.request, Reply.withContext(this -> reqCtx))
      openRequests += reqCtx
    }

    def handleResponse(response: Any, reqCtx: RequestContext): Option[RequestContext] = {
      response match {
        case _ if hostConnections(index) != this || openRequests.isEmpty || openRequests.head != reqCtx =>
          warning.log(connection.tag, "Unexpected response {} from connection {}, dropping...", response, index); None
        case x: HttpResponse => handleHttpResponse(x); None
        case x: ChunkedResponseStart => handleChunkedResponseStartResponse(x.message); None
        case x: MessageChunk => handleMessageChunkResponse(x); None
        case x: ChunkedMessageEnd => handleChunkedMessageEnd(x); None
        case Status.Failure(e) => requestContextForRetry(e)
        case HttpClientConnection.Closed(_, reason) =>
          resetConnection()
          requestContextForRetry(new RuntimeException("Connection closed before response reception, reason: " + reason))
        case x => warning.log(connection.tag, "Unexpected response {} for {}", x, format(reqCtx.request)); None
      }
    }

    def handleConnectionActorDeath(connectionActor: ActorRef) {
      if (connectionActor == this.connectionActor) updateHostConnection(new UnconnectedHostConnection(index))
    }

    private def handleHttpResponse(response: HttpResponse) {
      val RequestContext(request, _, commander) = openRequests.dequeue()
      val parsedResponse = prepareResponse(response, request)
      if (debug.enabled) debug.log(connection.tag, "Dispatching {} for {}", response, format(request))
      commander ! parsedResponse
      if (isCloseExpected(parsedResponse)) resetConnection()
    }

    private def handleChunkedResponseStartResponse(response: HttpResponse) {
      val RequestContext(request, _, commander) = openRequests.head
      val parsedResponse = ChunkedResponseStart(prepareResponse(response, request))
      if (debug.enabled) debug.log(connection.tag, "Dispatching {} for {}", parsedResponse, format(request))
      commander ! parsedResponse
      closeAfterChunkedMessageEnd = isCloseExpected(parsedResponse.message)
    }

    private def handleMessageChunkResponse(chunk: MessageChunk) {
      val RequestContext(request, _, commander) = openRequests.head
      if (debug.enabled)
        debug.log(connection.tag, "Dispatching {} byte message chunk for {}", chunk.body.length, format(request))
      commander ! chunk
    }

    private def handleChunkedMessageEnd(msg: ChunkedMessageEnd) {
      val RequestContext(request, _, commander) = openRequests.dequeue()
      if (debug.enabled) debug.log(connection.tag, "Dispatching {} for {}", msg, format(request))
      commander ! msg
      if (closeAfterChunkedMessageEnd) resetConnection()
    }

    private def prepareResponse(response: HttpResponse, request: HttpRequest) = {
      val (errors, parsedResponse) = response.parseHeaders
      if (warning.enabled && hostConnectorSettings.WarnOnIllegalHeaders && !errors.isEmpty)
        warning.log(connection.tag, "{} response to {}: {}", response.status.value, format(request), errors)
      parsedResponse
    }

    private def isCloseExpected(response: HttpResponse) = {
      import HttpProtocols._
      import HttpHeaders._
      val closeExpected = response.protocol match {
        case `HTTP/1.0` => !response.headers.exists(_ matches { case x: Connection if x.hasKeepAlive => })
        case `HTTP/1.1` => response.headers.exists(_ matches { case x: Connection if x.hasClose => })
      }
      if (closeExpected && openRequests.nonEmpty) {
        val error = new RuntimeException("Connection closed before arrival of response, apparently the server " +
          "doesn't support request pipelining")
        openRequests.foreach(ctx => ctx.commander ! error)
      }
      closeExpected
    }

    private def requestContextForRetry(error: Throwable): Option[RequestContext] = {
      val RequestContext(request, retriesLeft, commander) = openRequests.dequeue()
      if (request.canBeRetried && retriesLeft > 0) {
        if (debug.enabled) debug.log(connection.tag, "Received '{}' in response to {} with {} retries left, " +
          "retrying...", error, format(request), retriesLeft)
        Some(RequestContext(request, retriesLeft - 1, commander))
      } else {
        if (debug.enabled) debug.log(connection.tag, "Received '{}' in response to {} with no retries left, " +
          "dispatching error...", error, format(request))
        commander ! Status.Failure(error)
        None
      }
    }
  }
}

