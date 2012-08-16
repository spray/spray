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

import akka.event.LoggingAdapter
import akka.util.Duration
import cc.spray.can.server.StatsSupport.StatsHolder
import cc.spray.can.HttpCommand
import cc.spray.io.pipelining._
import cc.spray.io._
import cc.spray.http._


class HttpServer(ioWorker: IoWorker, messageHandler: MessageHandlerDispatch.MessageHandler,
                 settings: ServerSettings = ServerSettings())(implicit sslEngineProvider: ServerSSLEngineProvider)
  extends IoServer(ioWorker) with ConnectionActors {

  protected lazy val pipeline =
    HttpServer.pipeline(settings, messageHandler, timeoutResponse, statsHolder, log)

  protected lazy val statsHolder = new StatsHolder

  override def receive = super.receive orElse {
    case HttpServer.GetStats    => sender ! statsHolder.toStats
    case HttpServer.ClearStats  => statsHolder.clear()
  }

  override protected def createConnectionActor(handle: Handle): IoConnectionActor = new IoConnectionActor(handle) {
    override def receive = super.receive orElse {
      case x: HttpResponse => pipelines.commandPipeline(HttpCommand(x))
    }
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
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.AckSend                |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestChunkAggregation: listens to HttpMessagePart events, generates HttpRequest events
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.AckSend                |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | PipeliningLimiter: throttles incoming requests according to the PipeliningLimit, listens
   * |                    to HttpResponsePartRenderingContext commands and HttpRequestPart events,
   * |                    generates StopReading and ResumeReading commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.AckSend                | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | StatsSupport: listens to most commands and events to collect statistics
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.AckSend                | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestParsing: converts Received events to HttpMessagePart,
   * |                 generates HttpResponsePartRenderingContext (in case of errors)
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | HttpResponsePartRenderingContext
   *    | IoServer.AckSend                | IoServer.Tell
   *    | IoServer.Received               | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ResponseRendering: converts HttpResponsePartRenderingContext
   * |                    to Send and Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.AckSend                | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              | IoServer.StopReading
   *    |                                 | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ConnectionTimeouts: listens to Received events and Send commands and
   * |                     TickGenerator.Tick, generates Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.AckSend                | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              | IoServer.StopReading
   *    |                                 | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | SslTlsSupport: listens to event Send and Close commands and Received events,
   * |                provides transparent encryption/decryption in both directions
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.AckSend                | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              | IoServer.StopReading
   *    |                                 | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | TickGenerator: listens to Closed events,
   * |                dispatches TickGenerator.Tick events to the head of the event PL
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.AckSend                | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              | IoServer.StopReading
   *    |                                 | IoServer.ResumeReading
   *    |                                \/
   */
  private[can] def pipeline(settings: ServerSettings,
                            messageHandler: MessageHandlerDispatch.MessageHandler,
                            timeoutResponse: HttpRequest => HttpResponse,
                            statsHolder: => StatsHolder,
                            log: LoggingAdapter)
                           (implicit sslEngineProvider: ServerSSLEngineProvider): PipelineStage = {
    import settings.{StatsSupport => _, _}
    ServerFrontend(settings, messageHandler, timeoutResponse, log) >>
    (RequestChunkAggregationLimit > 0) ? RequestChunkAggregation(RequestChunkAggregationLimit.toInt) >>
    (PipeliningLimit > 0) ? PipeliningLimiter(settings.PipeliningLimit) >>
    settings.StatsSupport ? StatsSupport(statsHolder) >>
    RemoteAddressHeader ? RemoteAddressHeaderSupport() >>
    RequestParsing(ParserSettings, log) >>
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
  type ServerCommand = IoServer.ServerCommand
  type Bind = IoServer.Bind;                                  val Bind = IoServer.Bind
  val Unbind = IoServer.Unbind
  type Close = IoServer.Close;                                val Close = IoServer.Close
  type SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout;    val SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout
  type SetRequestTimeout = ServerFrontend.SetRequestTimeout;  val SetRequestTimeout = ServerFrontend.SetRequestTimeout
  type SetTimeoutTimeout = ServerFrontend.SetTimeoutTimeout;  val SetTimeoutTimeout = ServerFrontend.SetTimeoutTimeout
  case object ClearStats extends Command
  case object GetStats extends Command

  ////////////// EVENTS //////////////
  // HttpRequestParts +
  type Bound = IoServer.Bound;                          val Bound = IoServer.Bound
  type Unbound = IoServer.Unbound;                      val Unbound = IoServer.Unbound
  type Closed = IoServer.Closed;                        val Closed = IoServer.Closed
  type AckSend = IoServer.AckSend;                      val AckSend = IoServer.AckSend

}