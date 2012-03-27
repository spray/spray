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

package cc.spray
package can

import io._
import akka.event.LoggingAdapter
import akka.util.Duration
import model.{HttpHeader, HttpResponse, HttpRequest}
import pipelines.{MessageHandlerDispatch, TickGenerator, ConnectionTimeouts}
import com.typesafe.config.{ConfigFactory, Config}
import java.util.concurrent.TimeUnit

class HttpServer(ioWorker: IoWorker,
                 messageHandler: MessageHandlerDispatch.MessageHandler,
                 config: Config = ConfigFactory.load())
  extends IoServer(ioWorker) with ConnectionActors {

  protected lazy val pipeline = {
    val settings = new ServerSettings(config, ioWorker.settings.ConfirmSends)
    HttpServer.pipeline(settings, messageHandler, timeoutResponse, log)
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
   * | ServerFrontend: converts HttpMessagePart, IoPeer.Closed and IoPeer.SendCompleted to
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
   * | StatsSupport: listens to most commands and events to collect statistics
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | HttpMessagePart                 | HttpResponsePartRenderingContext
   *    | IoServer.Closed                 | IoServer.Tell
   *    | IoServer.SendCompleted          |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | RequestParsing: converts IoServer.Received to HttpMessagePart,
   * |                 generates HttpResponsePartRenderingContext (in case of errors)
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | HttpResponsePartRenderingContext
   *    | IoServer.SendCompleted          | IoServer.Tell
   *    | IoServer.Received               |
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ResponseRendering: converts HttpResponsePartRenderingContext
   * |                    to IoServer.Send and IoServer.Close
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.SendCompleted          | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | ConnectionTimeouts: listens to IoServer.Received and IoServer.Send and
   * |                     TickGenerator.Tick, generates IoServer.Close commands
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.SendCompleted          | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              |
   *    |                                \/
   * |------------------------------------------------------------------------------------------
   * | TickGenerator: listens to event IoServer.Closed,
   * |                dispatches TickGenerator.Tick events to the head of the event PL
   * |------------------------------------------------------------------------------------------
   *    /\                                |
   *    | IoServer.Closed                 | IoServer.Send
   *    | IoServer.SendCompleted          | IoServer.Close
   *    | IoServer.Received               | IoServer.Tell
   *    | TickGenerator.Tick              |
   *    |                                \/
   */
  private[can] def pipeline(settings: ServerSettings,
                            messageHandler: MessageHandlerDispatch.MessageHandler,
                            timeoutResponse: HttpRequest => HttpResponse,
                            log: LoggingAdapter): PipelineStage = {
    ServerFrontend(settings, messageHandler, timeoutResponse, log) ~>
    PipelineStage.optional(settings.PipeliningLimit > 0, PipeliningLimiter(settings.PipeliningLimit)) ~>
    PipelineStage.optional(settings.StatsSupport, StatsSupport()) ~>
    RequestParsing(settings.ParserSettings, log) ~>
    ResponseRendering(settings.ServerHeader, settings.ChunklessStreaming, settings.ResponseSizeHint) ~>
    PipelineStage.optional(settings.IdleTimeout > 0, ConnectionTimeouts(settings.IdleTimeout, log)) ~>
    PipelineStage.optional(
      settings.ReapingCycle > 0 && (settings.IdleTimeout > 0 || settings.RequestTimeout > 0),
      TickGenerator(Duration(settings.ReapingCycle, TimeUnit.MILLISECONDS))
    )
  }

  case class Stats(
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
  val ClearStats = StatsSupport.ClearStats
  val GetStats = StatsSupport.GetStats

  ////////////// EVENTS //////////////
  // HttpRequestParts +
  type Bound = IoServer.Bound;                          val Bound = IoServer.Bound
  type Unbound = IoServer.Unbound;                      val Unbound = IoServer.Unbound
  type Closed = IoServer.Closed;                        val Closed = IoServer.Closed
  type SendCompleted = IoServer.SendCompleted;          val SendCompleted = IoServer.SendCompleted
  type RequestTimeout = ServerFrontend.RequestTimeout;  val RequestTimeout = ServerFrontend.RequestTimeout

}