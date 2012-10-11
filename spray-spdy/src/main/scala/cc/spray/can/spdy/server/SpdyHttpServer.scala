package cc.spray.can.spdy
package server

import akka.event.LoggingAdapter
import akka.util.Duration
import cc.spray.io.pipelining._
import cc.spray.io._
import cc.spray.http._
import cc.spray.io.IOBridge
import cc.spray.io.pipelining.MessageHandler
import cc.spray.can.server._
import cc.spray.io.pipelining.ServerSSLEngineProvider
import cc.spray.io.IOServer
import cc.spray.io.ConnectionActors
import cc.spray.can.server.StatsSupport.StatsHolder
import cc.spray.io.Handle
import cc.spray.http.HttpRequest
import cc.spray.io.pipelining.PipelineStage
import cc.spray.io.pipelining.ConnectionTimeouts
import cc.spray.io.pipelining.SslTlsSupport
import cc.spray.io.pipelining.TickGenerator
import cc.spray.io.Command
import cc.spray.util.Reply

import pipeline._
import cc.spray.http.HttpResponse
import cc.spray.can.HttpCommand
import pipeline.SpdyStreamManager.SpdyContext
import scala.Some
import cc.spray.http.HttpResponse
import cc.spray.can.HttpCommand


class SpdyHttpServer(ioBridge: IOBridge, messageHandler: MessageHandler, settings: ServerSettings = ServerSettings())
                (implicit sslEngineProvider: ServerSSLEngineProvider) extends IOServer(ioBridge) with ConnectionActors {

  protected val statsHolder: Option[StatsHolder] =
    if (settings.StatsSupport) Some(new StatsHolder) else None

  override def receive = super.receive orElse {
    case HttpServer.GetStats    => statsHolder.foreach(holder => sender ! holder.toStats)
    case HttpServer.ClearStats  => statsHolder.foreach(_.clear())
  }

  protected val pipeline =
    SpdyHttpServer.pipeline(settings, messageHandler, timeoutResponse, statsHolder, log)

  override protected def createConnectionActor(handle: Handle): IOConnectionActor = new IOConnectionActor(handle) {
    override def receive = super.receive orElse {
      case Reply(msg: HttpMessagePart, ctx: SpdyContext) =>
        ctx.pipelines.commandPipeline(HttpCommand(msg))
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

object SpdyHttpServer {

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
  def pipeline(settings: ServerSettings,
                            messageHandler: MessageHandler,
                            timeoutResponse: HttpRequest => HttpResponse,
                            statsHolder: Option[StatsHolder],
                            log: LoggingAdapter)
                           (implicit sslEngineProvider: ServerSSLEngineProvider): PipelineStage = {
    import settings.{StatsSupport => _, _}
    SpdyStreamManager(messageHandler, HttpHelper.unwrapHttpEvent) {
      (RequestChunkAggregationLimit > 0) ? RequestChunkAggregation(RequestChunkAggregationLimit.toInt) >>
      (PipeliningLimit > 0) ? PipeliningLimiter(settings.PipeliningLimit) >>
      settings.StatsSupport ? StatsSupport(statsHolder.get) >>
      RemoteAddressHeader ? RemoteAddressHeaderSupport() >>
      HttpOnSpdy()
    } >>
    SpdyRendering() >>
    SpdyParsing() >>
    //(IdleTimeout > 0) ? ConnectionTimeouts(IdleTimeout, log) >>
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
  type SentOk = IOServer.SentOk;   val SentOk = IOServer.SentOk

}