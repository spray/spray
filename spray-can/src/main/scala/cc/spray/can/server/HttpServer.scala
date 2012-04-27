/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import cc.spray.can.model.{HttpHeader, HttpResponse, HttpRequest}
import cc.spray.io._
import akka.event.LoggingAdapter
import akka.util.Duration
import com.typesafe.config.{ConfigFactory, Config}
import java.util.concurrent.TimeUnit
import cc.spray.can.server.StatsSupport.StatsHolder
import pipelines._

class HttpServer(ioWorker: IoWorker,
                 messageHandler: MessageHandlerDispatch.MessageHandler,
                 config: Config = ConfigFactory.load)
                (implicit sslEngineProvider: ServerSSLEngineProvider)
  extends IoServer(ioWorker) with ConnectionActors {

  protected lazy val pipeline = {
    val settings = new ServerSettings(config, ioWorker.settings.ConfirmSends)
    HttpServer.pipeline(settings, messageHandler, timeoutResponse, statsHolder, log)
  }

  protected lazy val statsHolder = new StatsHolder

  override def receive = super.receive orElse {
    case HttpServer.GetStats    => sender ! statsHolder.toStats
    case HttpServer.ClearStats  => statsHolder.clear()
  }

  /**
   * This methods determines the HttpResponse to sent back to the client if both the request handling actor
   * as well as the timeout actor do not produce timely responses with regard to the configured timeout periods.
   */
  protected def timeoutResponse(request: HttpRequest): HttpResponse = HttpResponse(
    status = 500,
    headers = List(HttpHeader("Content-Type", "text/plain"))
  ).withBody {
    "Ooops! The server was not able to produce a timely response to your request.\n" +
    "Please try again in a short while!"
  }
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
   *    | IoServer.SendCompleted          |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestChunkAggregation: listens to HttpMessagePart events, generates HttpRequest events
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.SendCompleted          |
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
   *    | IoServer.SendCompleted          | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | StatsSupport: listens to most commands and events to collect statistics
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.SendCompleted          | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestParsing: converts Received events to HttpMessagePart,
   * |                 generates HttpResponsePartRenderingContext (in case of errors)
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | HttpResponsePartRenderingContext
   *    | IoServer.SendCompleted          | IoServer.Tell
   *    | IoServer.Received               | IoServer.StopReading
   *    | TickGenerator.Tick              | IoServer.ResumeReading
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ResponseRendering: converts HttpResponsePartRenderingContext
   * |                    to Send and Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.SendCompleted          | IoServer.Close
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
   *    | IoServer.SendCompleted          | IoServer.Close
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
   *    | IoServer.SendCompleted          | IoServer.Close
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
   *    | IoServer.SendCompleted          | IoServer.Close
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
    ServerFrontend(settings, messageHandler, timeoutResponse, log) ~>
    PipelineStage.optional(settings.RequestChunkAggregationLimit > 0,
      RequestChunkAggregation(settings.RequestChunkAggregationLimit.toInt)) ~>
    PipelineStage.optional(settings.PipeliningLimit > 0, PipeliningLimiter(settings.PipeliningLimit)) ~>
    PipelineStage.optional(settings.StatsSupport, StatsSupport(statsHolder)) ~>
    RequestParsing(settings.ParserSettings, log) ~>
    ResponseRendering(settings.ServerHeader, settings.ChunklessStreaming, settings.ResponseSizeHint.toInt) ~>
    PipelineStage.optional(settings.IdleTimeout > 0, ConnectionTimeouts(settings.IdleTimeout, log)) ~>
    PipelineStage.optional(settings.SSLEncryption, SslTlsSupport(sslEngineProvider, log)) ~>
    PipelineStage.optional(
      settings.ReapingCycle > 0 && (settings.IdleTimeout > 0 || settings.RequestTimeout > 0),
      TickGenerator(Duration(settings.ReapingCycle, TimeUnit.MILLISECONDS))
    )
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
  type SendCompleted = IoServer.SendCompleted;          val SendCompleted = IoServer.SendCompleted
  type RequestTimeout = ServerFrontend.RequestTimeout;  val RequestTimeout = ServerFrontend.RequestTimeout

}