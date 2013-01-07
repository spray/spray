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

package spray.can
package client

import akka.event.LoggingAdapter
import spray.http.{Confirmed, HttpRequestPart}
import spray.io._


/**
 * The lowest-level client-side HTTP transport.
 * Represents a single (but potentially long-living) HTTP connection to a specific host and port.
 */
class HttpClientConnection(settings: HttpClientConnectionSettings = HttpClientConnectionSettings())
                          (implicit sslEngineProvider: ClientSSLEngineProvider) extends IOClientConnection {

  override def pipelineStage: PipelineStage = HttpClientConnection.pipelineStage(settings, log)

  override def connected: Receive = super.connected orElse {
    case x: HttpRequestPart                  => pipelines.commandPipeline(HttpCommand(x))
    case x@ Confirmed(_: HttpRequestPart, _) => pipelines.commandPipeline(HttpCommand(x))
  }
}

object HttpClientConnection {

  private[can] def pipelineStage(settings: HttpClientConnectionSettings, log: LoggingAdapter)
                                (implicit sslEngineProvider: ClientSSLEngineProvider): PipelineStage = {
    import settings._
    ClientFrontend(RequestTimeout, log) >>
    ResponseChunkAggregation(ResponseChunkAggregationLimit.toInt) ? (ResponseChunkAggregationLimit > 0) >>
    ResponseParsing(ParserSettings, log) >>
    RequestRendering(settings) >>
    ConnectionTimeouts(IdleTimeout, log) ? (ReapingCycle > 0 && IdleTimeout > 0) >>
    SslTlsSupport(sslEngineProvider, log, encryptIfUntagged = false) >>
    TickGenerator(ReapingCycle) ? (ReapingCycle > 0 && (IdleTimeout > 0 || RequestTimeout > 0))
  }

  /**
   * Object to be used as `tag` member of `Connect` commands in order to activate SSL encryption on the connection.
   */
  val SslEnabled = ConnectionTag(encrypted = true)

  case class ConnectionTag(encrypted: Boolean, logMarker: String = "") extends SslTlsSupport.Enabling with LogMarking {
    def encrypt(ctx: PipelineContext): Boolean = encrypted
  }

  ////////////// COMMANDS //////////////
  // HttpRequestParts +
  type Connect           = IOClientConnection.Connect;        val Connect           = IOClientConnection.Connect
  type Close             = IOClientConnection.Close;          val Close             = IOClientConnection.Close
  type SetIdleTimeout    = ConnectionTimeouts.SetIdleTimeout; val SetIdleTimeout    = ConnectionTimeouts.SetIdleTimeout
  type SetRequestTimeout = ClientFrontend.SetRequestTimeout;  val SetRequestTimeout = ClientFrontend.SetRequestTimeout

  ////////////// EVENTS //////////////
  // HttpResponseParts +
  type Connected  = IOClientConnection.Connected;  val Connected  = IOClientConnection.Connected
  type Closed     = IOClientConnection.Closed;     val Closed     = IOClientConnection.Closed
}

