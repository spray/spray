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

import akka.event.Logging
import akka.actor._
import spray.can.client.{HttpClientConnection, HttpClientConnectionSettings}
import spray.http._
import spray.util._
import spray.io._


/**
 * A client-side HTTP transport to a specific host and port.
 * Automatically manages a number of connections and optionally retries failed requests.
 * Responds to incoming HttpRequests with the response (parts) coming back from the host.
 */
class HttpHostConnector(val host: String,
                        val port: Int = 80,
                        val hostConnectorSettings: HttpHostConnectorSettings = HttpHostConnectorSettings(),
                        val clientConnectionSettings: HttpClientConnectionSettings = HttpClientConnectionSettings(),
                        val defaultConnectionTag: Any = ())
                       (implicit sslEngineProvider: ClientSSLEngineProvider)
  extends Actor with SprayActorLogging with HostConnections with DispatchStrategies {
  import HttpHostConnector._

  val debug = TaggableLog(log, Logging.DebugLevel)
  def warning = TaggableLog(log, Logging.WarningLevel) // rarely used, so we don't occupy a field
  val dispatchStrategy =
    if (hostConnectorSettings.PipelineRequests) new PipelinedStrategy else new NonPipelinedStrategy
  val hostConnections =
    Array.tabulate[HostConnection](hostConnectorSettings.MaxConnections)(i => new UnconnectedHostConnection(i))

  def receive = {
    case x: HttpRequest =>
      dispatchStrategy.dispatch(RequestContext(x, hostConnectorSettings.MaxRetries, sender))

    case Reply(response, (conn: ConnectedHostConnection, reqCtx: RequestContext)) =>
      val retryRequestContext = conn.handleResponse(response, reqCtx)
      retryRequestContext.foreach(dispatchStrategy.dispatch)
      dispatchStrategy.onStateChange()

    case Reply(HttpClientConnection.Connected(connection), conn: ConnectingHostConnection) =>
      conn.connected(connection)

    case Reply(error: Status.Failure, conn: ConnectingHostConnection) =>
      conn.connectFailed(error)
      dispatchStrategy.onStateChange()

    case Reply(HttpClientConnection.Closed(connection, reason), conn: HostConnection) =>
      debug.log(connection.tag, "Connection {} lost due to {}", conn.index, reason)
      conn.resetConnection()
      dispatchStrategy.onStateChange()

    case Terminated(connectionActor) => hostConnections.foreach(_.handleConnectionActorDeath(connectionActor))

    case RequestIdleStatus =>
      sender ! IdleStatus(hostConnections.forall(_.isInstanceOf[UnconnectedHostConnection]), host, port)
  }

  def updateHostConnection(conn: HostConnection) {
    hostConnections(conn.index) = conn
  }

  // override for provision of custom connection tags
  def tagForConnection(index: Int) = defaultConnectionTag

  def format(request: HttpRequest) =
    if (request.uri.startsWith("http")) "%s request to %s".format(request.method, request.uri)
    else "%s request to http://%s:%s%s".format(request.method, host, port, request.uri)
}

object HttpHostConnector {
  case object RequestIdleStatus
  case class IdleStatus(idle: Boolean, host: String, port: Int)
}