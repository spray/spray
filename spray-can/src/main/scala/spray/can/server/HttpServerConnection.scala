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

package spray.can.server

import java.net.InetSocketAddress
import akka.util.Duration
import akka.actor.{ SupervisorStrategy, Terminated, ReceiveTimeout, ActorRef }
import akka.io.{ ExtraStrategies, Tcp }
import spray.can.server.StatsSupport.StatsHolder
import spray.io._
import spray.can.Http
import spray.http.{ SetTimeoutTimeout, SetRequestTimeout }

private[can] class HttpServerConnection(tcpConnection: ActorRef,
                                        userLevelListener: ActorRef,
                                        pipelineStage: RawPipelineStage[ServerFrontend.Context with SslTlsContext],
                                        remoteAddress: InetSocketAddress,
                                        localAddress: InetSocketAddress,
                                        settings: ServerSettings)(implicit val sslEngineProvider: ServerSSLEngineProvider)
    extends ConnectionHandler { actor ⇒

  userLevelListener ! Http.Connected(remoteAddress, localAddress)

  context.setReceiveTimeout(settings.registrationTimeout)

  // we cannot sensibly recover from crashes
  override def supervisorStrategy = ExtraStrategies.stoppingStrategy

  def receive: Receive = {
    case Tcp.Register(handler, keepOpenOnPeerClosed, _)         ⇒ register(handler, keepOpenOnPeerClosed)

    case Http.Register(handler, keepOpenOnPeerClosed, fastPath) ⇒ register(handler, keepOpenOnPeerClosed, fastPath)

    case ReceiveTimeout ⇒
      log.warning("Configured registration timeout of {} expired, stopping", settings.registrationTimeout)
      tcpConnection ! Http.Close
      context.watch(tcpConnection)
      context.become {
        case _: Http.ConnectionClosed    ⇒ // ignore
        case Terminated(`tcpConnection`) ⇒ context.stop(self)
        case ReceiveTimeout              ⇒ context.stop(self)
      }
  }

  def register(handler: ActorRef, keepOpenOnPeerClosed: Boolean, fastPath: Http.FastPath = Http.EmptyFastPath): Unit = {
    context.resetReceiveTimeout()
    tcpConnection ! Tcp.Register(self, keepOpenOnPeerClosed)
    context.watch(tcpConnection)
    context.watch(handler)
    context.become(running(tcpConnection, pipelineStage, pipelineContext(handler, fastPath), keepOpenOnPeerClosed = false))
  }

  def pipelineContext(_handler: ActorRef, _fastPath: Http.FastPath) = new SslTlsContext with ServerFrontend.Context {
    def handler = _handler
    def fastPath = _fastPath
    def actorContext = context
    def remoteAddress = actor.remoteAddress
    def localAddress = actor.localAddress
    def log = actor.log
    def sslEngine = sslEngineProvider(this)
  }

  override def running(tcpConnection: ActorRef, pipelines: Pipelines): Receive =
    super.running(tcpConnection, pipelines) orElse {
      case x: SetRequestTimeout ⇒ pipelines.commandPipeline(CommandWrapper(x))
      case x: SetTimeoutTimeout ⇒ pipelines.commandPipeline(CommandWrapper(x))
    }
}

private[can] object HttpServerConnection {

  /**
   * The HttpServerConnection pipeline setup:
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
  def pipelineStage(settings: ServerSettings, statsHolder: Option[StatsHolder]) = {
    import settings._
    ServerFrontend(settings) >>
      (requestChunkAggregationLimit > 0) ? RequestChunkAggregation(requestChunkAggregationLimit) >>
      (pipeliningLimit > 0) ? PipeliningLimiter(pipeliningLimit) >>
      statsSupport ? StatsSupport(statsHolder.get) >>
      remoteAddressHeader ? RemoteAddressHeaderSupport >>
      RequestParsing(settings) >>
      ResponseRendering(settings) >>
      (reapingCycle.isFinite && idleTimeout.isFinite) ? ConnectionTimeouts(idleTimeout) >>
      sslEncryption ? SslTlsSupport >>
      (reapingCycle.isFinite && (idleTimeout.isFinite || requestTimeout.isFinite)) ? TickGenerator(reapingCycle)
  }
}
