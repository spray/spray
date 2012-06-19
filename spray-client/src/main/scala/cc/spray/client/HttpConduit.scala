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

package cc.spray.client

import cc.spray.http._
import cc.spray.can.client.HttpClient
import cc.spray.util._
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import akka.dispatch.Promise
import collection.mutable.Queue
import cc.spray.SprayCanConversions
import SprayCanConversions._
import cc.spray.io.{CleanClose, ConnectionClosedReason, Handle}


class HttpConduit(httpClient: ActorRef,
                  host: String,
                  port: Int = 80,
                  dispatchStrategy: DispatchStrategy = DispatchStrategies.NonPipelined(),
                  config: Config = ConfigFactory.load())
                 (implicit refFactory: ActorRefFactory) extends MessagePipelining {

  private val settings = new ConduitSettings(config)
  private val mainActor = refFactory.actorOf(Props(new MainActor))

  val sendReceive: SendReceive = { request =>
    val promise = Promise[HttpResponse]()(refFactory.messageDispatcher)
    mainActor ! RequestContext(request, settings.MaxRetries, promise)
    promise
  }

  def close() {
    mainActor ! Stop
  }

  private case object Stop
  private case class RequestContext(request: HttpRequest, retriesLeft: Int,
                                      result: Promise[HttpResponse]) extends HttpRequestContext {
    def withRetriesDecremented = copy(retriesLeft = retriesLeft - 1)
  }

  private class MainActor extends Actor with ActorLogging {
    val conns = Vector.tabulate(settings.MaxConnections)(i => new Conn(i + 1))
    context.watch(httpClient)

    def receive = {
      case x: RequestContext =>
        dispatchStrategy.dispatch(x, conns)

      case Reply(response: cc.spray.can.model.HttpResponse, (conn: Conn, ctx: RequestContext, _)) =>
        conn.deliverResponse(ctx.request, fromSprayCanResponse(response), ctx.result)
        dispatchStrategy.onStateChange(conns)

      case Reply(_: HttpClient.AckSend, _) =>
        // ignore

      case Reply(problem, (conn: Conn, ctx: RequestContext, handle: Handle)) =>
        val error = problem match {
          case Status.Failure(error) => error
          case HttpClient.Closed(_, reason) => new RuntimeException("Connection closed, reason: " + reason)
        }
        if (ctx.request.method == HttpMethods.POST) conn.deliverError(ctx, error)
        else conn.retry(ctx, handle, error).foreach(dispatchStrategy.dispatch(_, conns))
        dispatchStrategy.onStateChange(conns)

      case Reply(HttpClient.Connected(handle), conn: Conn) =>
        conn.connected(handle)

      case Reply(Status.Failure(error), conn: Conn) =>
        conn.connectFailed(error)
        dispatchStrategy.onStateChange(conns)

      case Reply(HttpClient.Closed(handle, reason), conn: Conn) =>
        conn.closed(handle, reason)
        dispatchStrategy.onStateChange(conns)

      case Terminated(client) if client == httpClient =>
        stop()

      case Stop =>
        context.unwatch(httpClient)
        stop()
    }

    def stop() {
      conns.foreach(_.close())
      context.stop(self)
    }

    def requestString(request: HttpRequest) =
      "%s request to http://%s:%s%s".format(request.method, host, port, request.uri)

    class Conn(index: Int) extends HttpConn {
      private sealed trait ConnectionState
      private case object Unconnected extends ConnectionState
      private case object Connecting extends ConnectionState
      private case class Connected(handle: Handle) extends ConnectionState

      private var connection: ConnectionState = Unconnected
      private val pendingRequests = Queue.empty[RequestContext]
      var pendingResponses: Int = -1

      def dispatch(ctx: HttpRequestContext) {
        connection match {
          case Unconnected =>
            log.debug("Opening connection {} to {}:{}", index, host, port)
            pendingResponses = 0
            connection = Connecting
            httpClient.tell(HttpClient.Connect(host, port), Reply.withContext(this))
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
        handle.handler.tell(toSprayCanRequest(ctx.request), Reply.withContext((this, ctx, handle)))
      }

      def connected(handle: Handle) {
        connection = Connected(handle)
        log.debug("Connected connection {}, dispatching {} pending requests", index, pendingRequests.length)
        while (!pendingRequests.isEmpty) dispatch(pendingRequests.dequeue(), handle)
      }

      def connectFailed(error: Throwable) {
        while (!pendingRequests.isEmpty) pendingRequests.dequeue().result.failure(error)
        clear()
      }

      def deliverResponse(request: HttpRequest, response: HttpResponse, result: Promise[HttpResponse]) {
        import HttpProtocols._
        def closeExpected = response.protocol match {
          case `HTTP/1.0` => !response.headers.exists(_ matches { case HttpHeaders.Connection(Seq("Keep-Alive")) => })
          case `HTTP/1.1` => response.headers.exists(_ matches { case HttpHeaders.Connection(Seq("close")) => })
        }
        log.debug("Dispatching {} response to {}", response.status.value, requestString(request))
        result.success(response)
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
        ctx.result.failure(error)
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
    }
  }

}