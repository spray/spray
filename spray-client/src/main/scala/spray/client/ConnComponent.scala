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

import collection.mutable.Queue
import akka.dispatch.Promise
import spray.can.client.HttpClient
import spray.util._
import spray.io._
import spray.http._
import akka.actor.{Status, ActorRef}


trait ConnComponent {
  this: HttpConduit =>

  class Conn(index: Int) extends HttpConn {
    import Conn._

    private var connection: ConnectionState = Unconnected
    private val pendingRequests = Queue.empty[RequestContext]
    var pendingResponses: Int = -1

    def dispatch(ctx: HttpRequestContext) {
      connection match {
        case Unconnected =>
          log.debug("Opening connection {} to {}:{}", index, host, port)
          pendingResponses = 0
          connection = Connecting
          import HttpClient._
          val connect = if (sslEnabled) Connect(host, port, SslEnabled) else Connect(host, port)
          httpClient.tell(connect, Reply.withContext(this))
          dispatch(ctx)

        case Connecting =>
          pendingRequests.enqueue(ctx.asInstanceOf[RequestContext])
          pendingResponses += 1

        case Connected(handle) =>
          dispatch(ctx, handle)
          pendingResponses += 1
      }
    }

    def dispatch(ctx: HttpRequestContext, handle: Handle) {
      log.debug("Dispatching {} across connection {}", requestString(ctx.request), index)
      handle.handler.tell(ctx.request, Reply.withContext((this, ctx, handle)))
    }

    def connected(handle: Handle) {
      connection = Connected(handle)
      log.debug("Connected connection {}, dispatching {} pending requests", index, pendingRequests.length)
      while (!pendingRequests.isEmpty) dispatch(pendingRequests.dequeue(), handle)
    }

    def connectFailed(error: Throwable) {
      while (!pendingRequests.isEmpty) pendingRequests.dequeue().sender ! Status.Failure(error)
      clear()
    }

    def deliverResponse(request: HttpRequest, response: HttpResponse, sender: ActorRef) {
      import HttpProtocols._
      import HttpHeaders._
      def closeExpected = response.protocol match {
        case `HTTP/1.0` => !response.headers.exists(_ matches { case x: Connection if x.hasKeepAlive => })
        case `HTTP/1.1` => response.headers.exists(_ matches { case x: Connection if x.hasClose => })
      }
      log.debug("Dispatching {} response to {}", response.status.value, requestString(request))
      val (errors, parsedResponse) = response.parseHeaders
      if (settings.WarnOnIllegalHeaders && !errors.isEmpty)
        log.warning("Problem with {} response to {}, {}", response.status.value, requestString(request), errors)
      sender ! parsedResponse
      if (closeExpected) clear()
      else pendingResponses -= 1
    }

    def retry(ctx: RequestContext, errorHandle: Handle, error: Throwable): Option[RequestContext] = {
      def retryWith(ctx: RequestContext) = {
        log.debug("Received '{}' in response to {} with {} retries left, retrying...",
          error, requestString(ctx.request), ctx.retriesLeft)
        Some(ctx)
      }
      if (connection == Connected(errorHandle)) {
        // only the first of a potential series of failed requests on the connection gets here
        clear()
        if (ctx.retriesLeft == 0) {
          deliverError(ctx, error)
          None
        } else retryWith(ctx.withRetriesDecremented)
      } else retryWith(ctx)
    }

    def deliverError(ctx: RequestContext, error: Throwable) {
      log.debug("Received '{}' in response to {} with no retries left, dispatching error...",
        error, requestString(ctx.request))
      ctx.sender ! Status.Failure(error)
    }

    def closed(handle: Handle, reason: ConnectionClosedReason) {
      if (connection == Connected(handle)) {
        log.debug("Connection {} lost due to {}", index, reason)
        clear()
      }
    }

    def close() {
      connection match {
        case Connected(handle) =>
          log.debug("Closing connection {} to due HttpConduit being closed", index)
          handle.handler ! HttpClient.Close(CleanClose)
        case _ =>
      }
    }

    def clear() {
      pendingResponses = -1
      connection = Unconnected
    }

    def requestString(request: HttpRequest) =
      "%s request to http://%s:%s%s".format(request.method, host, port, request.uri)
  }

  object Conn {
    private sealed trait ConnectionState
    private case object Unconnected extends ConnectionState
    private case object Connecting extends ConnectionState
    private case class Connected(handle: Handle) extends ConnectionState
  }
}

