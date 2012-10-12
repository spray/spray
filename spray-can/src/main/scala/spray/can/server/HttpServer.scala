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
import spray.can.server.StatsSupport.StatsHolder
import spray.can.HttpCommand
import spray.io._
import spray.http._


class HttpServer(ioBridge: IOBridge, messageHandler: MessageHandler, settings: ServerSettings = ServerSettings())
                (implicit sslEngineProvider: ServerSSLEngineProvider) extends IOServer(ioBridge) with ConnectionActors {

  protected val statsHolder: Option[StatsHolder] =
    if (settings.StatsSupport) Some(new StatsHolder) else None

  protected val pipeline =
    HttpServer.pipeline(settings, messageHandler, timeoutResponse, statsHolder, log)

  override def receive = super.receive orElse {
    case HttpServer.GetStats    => statsHolder.foreach(holder => sender ! holder.toStats)
    case HttpServer.ClearStats  => statsHolder.foreach(_.clear())
  }

  /**
   * This methods determines the HttpResponse to sent back to the client if both the request handling actor
   * as well as the timeout actor do not produce timely responses with regard to the configured timeout periods.
   */
  protected def timeoutResponse(request: HttpRequest): HttpResponse = HttpResponse(
    status = 500,
    entity = "Ooops! The server was not able to produce a timely response to your request.\n" +
      "Please try again in a short while!"
  )
}

object HttpServer {

  /**
   * The HttpServer pipelines setup:
   *
   * |------------------------------------------------------------------------------------------
   * | ServerFrontend: converts HttpMessagePart, Closed and SendCompleted events to
   * |                 MessageHandlerDispatch.DispatchCommand,
   * |                 generates HttpResponsePartRenderingContext
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IOServer.Closed                 | IOServer.Tell
   *    | IOServer.SentOk                |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestChunkAggregation: listens to HttpMessagePart events, generates HttpRequest events
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IOServer.Closed                 | IOServer.Tell
   *    | IOServer.SentOk                |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | PipeliningLimiter: throttles incoming requests according to the PipeliningLimit, listens
   * |                    to HttpResponsePartRenderingContext commands and HttpRequestPart events,
   * |                    generates StopReading and ResumeReading commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IOServer.Closed                 | IOServer.Tell
   *    | IOServer.SentOk                | IOServer.StopReading
   *    | TickGenerator.Tick              | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | StatsSupport: listens to most commands and events to collect statistics
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IOServer.Closed                 | IOServer.Tell
   *    | IOServer.SentOk                | IOServer.StopReading
   *    | TickGenerator.Tick              | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RemoteAddressHeaderSupport: add `Remote-Address` headers to incoming requests
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IOServer.Closed                 | IOServer.Tell
   *    | IOServer.SentOk                | IOServer.StopReading
   *    | TickGenerator.Tick              | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestParsing: converts Received events to HttpMessagePart,
   * |                 generates HttpResponsePartRenderingContext (in case of errors)
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IOServer.Closed                 | HttpResponsePartRenderingContext
   *    | IOServer.SentOk                | IOServer.Tell
   *    | IOServer.Received               | IOServer.StopReading
   *    | TickGenerator.Tick              | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ResponseRendering: converts HttpResponsePartRenderingContext
   * |                    to Send and Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IOServer.Closed                 | IOServer.Send
   *    | IOServer.SentOk                | IOServer.Close
   *    | IOServer.Received               | IOServer.Tell
   *    | TickGenerator.Tick              | IOServer.StopReading
   *    |                                 | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ConnectionTimeouts: listens to Received events and Send commands and
   * |                     TickGenerator.Tick, generates Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IOServer.Closed                 | IOServer.Send
   *    | IOServer.SentOk                | IOServer.Close
   *    | IOServer.Received               | IOServer.Tell
   *    | TickGenerator.Tick              | IOServer.StopReading
   *    |                                 | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | SslTlsSupport: listens to event Send and Close commands and Received events,
   * |                provides transparent encryption/decryption in both directions
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IOServer.Closed                 | IOServer.Send
   *    | IOServer.SentOk                | IOServer.Close
   *    | IOServer.Received               | IOServer.Tell
   *    | TickGenerator.Tick              | IOServer.StopReading
   *    |                                 | IOServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | TickGenerator: listens to Closed events,
   * |                dispatches TickGenerator.Tick events to the head of the event PL
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IOServer.Closed                 | IOServer.Send
   *    | IOServer.SentOk                | IOServer.Close
   *    | IOServer.Received               | IOServer.Tell
   *    | TickGenerator.Tick              | IOServer.StopReading
   *    |                                 | IOServer.ResumeReading
   *    |                                \/
   */
  private[can] def pipeline(settings: ServerSettings,
                            messageHandler: MessageHandler,
                            timeoutResponse: HttpRequest => HttpResponse,
                            statsHolder: Option[StatsHolder],
                            log: LoggingAdapter)
                           (implicit sslEngineProvider: ServerSSLEngineProvider): PipelineStage = {
    import settings.{StatsSupport => _, _}
    ServerFrontend(settings, messageHandler, timeoutResponse, log) >>
    (RequestChunkAggregationLimit > 0) ? RequestChunkAggregation(RequestChunkAggregationLimit.toInt) >>
    (PipeliningLimit > 0) ? PipeliningLimiter(settings.PipeliningLimit) >>
    settings.StatsSupport ? StatsSupport(statsHolder.get) >>
    RemoteAddressHeader ? RemoteAddressHeaderSupport() >>
    RequestParsing(ParserSettings, VerboseErrorMessages, log) >>
    ResponseRendering(settings) >>
    (IdleTimeout > 0) ? ConnectionTimeouts(IdleTimeout, log) >>
    SSLEncryption ? SslTlsSupport(sslEngineProvider, log) >>
    (ReapingCycle > 0 && (IdleTimeout > 0 || RequestTimeout > 0)) ? TickGenerator(ReapingCycle)
  }

  case class Stats(
    uptime: Duration,
    totalRequests: Long,
    openRequests: Long,
    maxOpenRequests: Long,
    totalConnections: Long,
    openConnections: Long,
    maxOpenConnections: Long,
    requestTimeouts: Long,
    idleTimeouts: Long
  )

  ////////////// COMMANDS //////////////
  // HttpResponseParts +
  type ServerCommand = IOServer.ServerCommand
  type Bind = IOServer.Bind;                                  val Bind = IOServer.Bind
  val Unbind = IOServer.Unbind
  type Close = IOServer.Close;                                val Close = IOServer.Close
  type SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout;    val SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout
  type SetRequestTimeout = ServerFrontend.SetRequestTimeout;  val SetRequestTimeout = ServerFrontend.SetRequestTimeout
  type SetTimeoutTimeout = ServerFrontend.SetTimeoutTimeout;  val SetTimeoutTimeout = ServerFrontend.SetTimeoutTimeout
  case object ClearStats extends Command
  case object GetStats extends Command

  ////////////// EVENTS //////////////
  // HttpRequestParts +
  type Bound = IOServer.Bound;     val Bound = IOServer.Bound
  type Unbound = IOServer.Unbound; val Unbound = IOServer.Unbound
  type Closed = IOServer.Closed;   val Closed = IOServer.Closed
}